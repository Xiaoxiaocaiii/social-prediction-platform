package com.prediction.socialnetwork.reader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.SourceFormat;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SocialNetworkFileReader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String UTF8 = "utf8";
    private static final String UTF8_BOM = "utf8_bom";

    public FileReadResult read(Path path, DatasetFileDescriptor descriptor, boolean builtin) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(descriptor, "descriptor");
        try (InputStream input = Files.newInputStream(path)) {
            return read(input, descriptor, builtin);
        } catch (IOException exception) {
            return failedResult(descriptor, "FILE_OPEN_FAILED", exception.getMessage());
        }
    }

    public FileReadResult read(InputStream input, DatasetFileDescriptor descriptor, boolean builtin) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(descriptor, "descriptor");
        try (InputStream encodedInput = input) {
            return switch (descriptor.format()) {
                case CSV -> readCsv(encodedInput, descriptor, builtin);
                case JSONL -> readJsonl(encodedInput, descriptor, builtin);
            };
        } catch (IOException exception) {
            return failedResult(descriptor, "FILE_READ_FAILED", exception.getMessage());
        }
    }

    public static List<String> parseFollowingList(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        String value = rawValue.trim();
        if (!value.startsWith("[") || !value.endsWith("]")) {
            throw new IllegalArgumentException("following_list must be a Python list string");
        }
        String content = value.substring(1, value.length() - 1).trim();
        if (content.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : content.split(",")) {
            String normalized = item.trim();
            if ((normalized.startsWith("'") && normalized.endsWith("'"))
                    || (normalized.startsWith("\"") && normalized.endsWith("\""))) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("following_list contains an empty target");
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private FileReadResult readCsv(InputStream input, DatasetFileDescriptor descriptor, boolean builtin)
            throws IOException {
        DecodedInput decoded = decode(input);
        List<RawRecord> records = new ArrayList<>();
        List<FileReadIssue> issues = new ArrayList<>();
        int rowCount = 0;
        int okCount = 0;
        int warningCount = 0;
        int errorCount = 0;
        int skippedCount = 0;
        HeaderStatus headerStatus = HeaderStatus.MISSING;

        try (Reader reader = decoded.reader()) {
            CsvRecordReader csv = new CsvRecordReader(reader);
            CsvRow header = csv.next();
            if (header == null || isBlankRow(header.values())) {
                issues.add(new FileReadIssue(1, "HEADER_MISSING", "CSV must contain a header row"));
                return result(records, descriptor, decoded.encoding(), headerStatus, rowCount,
                        okCount, warningCount, errorCount, skippedCount, issues);
            }

            List<String> mappedHeaders = mapHeaders(header.values(), descriptor.role(), builtin);
            if (mappedHeaders == null || hasDuplicate(mappedHeaders)
                    || !hasRequiredFields(mappedHeaders, descriptor.role(), builtin)) {
                issues.add(new FileReadIssue(header.startLine(), "HEADER_INVALID",
                        "CSV header does not match the required fields for " + descriptor.role()));
                headerStatus = HeaderStatus.INVALID;
                return result(records, descriptor, decoded.encoding(), headerStatus, rowCount,
                        okCount, warningCount, errorCount, skippedCount, issues);
            }
            headerStatus = HeaderStatus.VALID;

            CsvRow row;
            while ((row = csv.next()) != null) {
                rowCount++;
                if (isBlankRow(row.values())) {
                    skippedCount++;
                    issues.add(new FileReadIssue(row.startLine(), "EMPTY_ROW", "Blank CSV row skipped"));
                    continue;
                }
                if (row.values().size() != mappedHeaders.size()) {
                    errorCount++;
                    issues.add(new FileReadIssue(row.startLine(), "COLUMN_COUNT_MISMATCH",
                            "CSV row has " + row.values().size() + " columns; expected "
                                    + mappedHeaders.size()));
                    continue;
                }
                Map<String, String> fields = toFieldMap(mappedHeaders, row.values());
                RawRecord record = new RawRecord(
                        descriptor.logicalFileName(), row.startLine(), fields, FileReadStatus.READ_OK);
                records.add(record);
                okCount++;
            }
        } catch (CsvParseException exception) {
            errorCount++;
            issues.add(new FileReadIssue(exception.lineNumber, "CSV_PARSE_FAILED", exception.getMessage()));
        } catch (CharacterCodingException exception) {
            errorCount++;
            issues.add(new FileReadIssue(0, "UNSUPPORTED_ENCODING",
                    "Only UTF-8 and UTF-8 BOM files are supported"));
        }
        return result(records, descriptor, decoded.encoding(), headerStatus, rowCount,
                okCount, warningCount, errorCount, skippedCount, issues);
    }

    private FileReadResult readJsonl(InputStream input, DatasetFileDescriptor descriptor, boolean builtin)
            throws IOException {
        DecodedInput decoded = decode(input);
        List<RawRecord> records = new ArrayList<>();
        List<FileReadIssue> issues = new ArrayList<>();
        int rowCount = 0;
        int okCount = 0;
        int warningCount = 0;
        int errorCount = 0;
        int skippedCount = 0;
        HeaderStatus headerStatus = HeaderStatus.VALID;

        try (BufferedReader reader = new BufferedReader(decoded.reader())) {
            String line;
            while ((line = reader.readLine()) != null) {
                rowCount++;
                int lineNumber = rowCount;
                if (line.isBlank()) {
                    skippedCount++;
                    issues.add(new FileReadIssue(lineNumber, "EMPTY_LINE", "Blank JSONL line skipped"));
                    continue;
                }
                try {
                    JsonNode object = OBJECT_MAPPER.readTree(line);
                    if (object == null || !object.isObject()) {
                        throw new IllegalArgumentException("JSONL line must be an object");
                    }
                    Map<String, String> fields = mapJsonFields(object, descriptor.role(), builtin);
                    if (!hasRequiredFields(fields.keySet(), descriptor.role(), builtin)) {
                        errorCount++;
                        issues.add(new FileReadIssue(lineNumber, "REQUIRED_FIELD_MISSING",
                                "JSONL object is missing a required field"));
                        continue;
                    }
                    records.add(new RawRecord(
                            descriptor.logicalFileName(), lineNumber, fields, FileReadStatus.READ_OK));
                    okCount++;
                } catch (Exception exception) {
                    errorCount++;
                    issues.add(new FileReadIssue(lineNumber, "JSONL_PARSE_FAILED", exception.getMessage()));
                }
            }
        } catch (CharacterCodingException exception) {
            errorCount++;
            issues.add(new FileReadIssue(0, "UNSUPPORTED_ENCODING",
                    "Only UTF-8 and UTF-8 BOM files are supported"));
        }
        return result(records, descriptor, decoded.encoding(), headerStatus, rowCount,
                okCount, warningCount, errorCount, skippedCount, issues);
    }

    private Map<String, String> mapJsonFields(JsonNode object, DatasetFileRole role, boolean builtin) {
        Map<String, String> fields = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = object.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            String mappedName = mapHeader(entry.getKey(), role, builtin);
            fields.put(mappedName, jsonValue(entry.getValue()));
        }
        return fields;
    }

    private static String jsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private static Map<String, String> toFieldMap(List<String> headers, List<String> values) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            fields.put(headers.get(i), values.get(i));
        }
        return fields;
    }

    private static List<String> mapHeaders(List<String> headers, DatasetFileRole role, boolean builtin) {
        List<String> mapped = new ArrayList<>();
        for (String header : headers) {
            mapped.add(mapHeader(header, role, builtin));
        }
        return mapped;
    }

    private static String mapHeader(String header, DatasetFileRole role, boolean builtin) {
        String normalized = header == null ? "" : header.strip();
        if (!builtin) {
            return normalized;
        }
        return switch (role) {
            case USERS -> switch (normalized) {
                case "账号ID" -> "user_id";
                case "用户名" -> "username";
                case "显示名称" -> "display_name";
                case "自我介绍" -> "bio";
                case "粉丝数" -> "followers_count";
                case "关注数" -> "following_count";
                case "推文总数" -> "tweet_count";
                case "账号创建时间" -> "created_at";
                case "注册地" -> "location";
                default -> normalized;
            };
            case RELATIONSHIPS -> switch (normalized) {
                case "user_id" -> "source_user_id";
                default -> normalized;
            };
            case TWEETS -> switch (normalized) {
                case "发布时间" -> "created_at";
                case "推文原文" -> "text";
                case "点赞数" -> "like_count";
                case "评论数" -> "reply_count";
                case "转发数" -> "repost_count";
                case "书签数" -> "bookmark_count";
                default -> normalized;
            };
            case UNKNOWN -> normalized;
        };
    }

    private static boolean hasRequiredFields(Iterable<String> fields, DatasetFileRole role, boolean builtin) {
        List<String> required = switch (role) {
            case USERS -> List.of("user_id");
            case RELATIONSHIPS -> builtin
                    ? List.of("source_user_id", "following_list")
                    : List.of("source_user_id", "target_user_id");
            case TWEETS -> builtin
                    ? List.of("tweet_id", "source_folder", "created_at", "text")
                    : List.of("tweet_id", "user_id", "created_at", "text");
            case UNKNOWN -> List.of();
        };
        List<String> available = new ArrayList<>();
        fields.forEach(available::add);
        return available.containsAll(required);
    }

    private static boolean hasDuplicate(List<String> headers) {
        return headers.size() != headers.stream().distinct().count();
    }

    private static boolean isBlankRow(List<String> values) {
        return values.isEmpty() || values.stream().allMatch(value -> value == null || value.isBlank());
    }

    private static DecodedInput decode(InputStream input) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(input);
        byte[] prefix = buffered.readNBytes(3);
        boolean utf8Bom = prefix.length >= 3
                && (prefix[0] & 0xFF) == 0xEF
                && (prefix[1] & 0xFF) == 0xBB
                && (prefix[2] & 0xFF) == 0xBF;
        boolean utf16Bom = prefix.length >= 2
                && (((prefix[0] & 0xFF) == 0xFE && (prefix[1] & 0xFF) == 0xFF)
                || ((prefix[0] & 0xFF) == 0xFF && (prefix[1] & 0xFF) == 0xFE));
        if (utf16Bom) {
            throw new CharacterCodingException();
        }
        InputStream content = utf8Bom
                ? buffered
                : new SequenceInputStream(new ByteArrayInputStream(prefix), buffered);
        CharsetDecoderReader reader = new CharsetDecoderReader(content, StandardCharsets.UTF_8);
        return new DecodedInput(reader, utf8Bom ? UTF8_BOM : UTF8);
    }

    private static FileReadResult failedResult(
            DatasetFileDescriptor descriptor, String code, String message) {
        FileReadIssue issue = new FileReadIssue(0, code, message == null ? code : message);
        return result(List.of(), descriptor, "unknown", HeaderStatus.INVALID,
                0, 0, 0, 1, 0, List.of(issue));
    }

    private static FileReadResult result(
            List<RawRecord> records,
            DatasetFileDescriptor descriptor,
            String encoding,
            HeaderStatus headerStatus,
            int rowCount,
            int okCount,
            int warningCount,
            int errorCount,
            int skippedCount,
            List<FileReadIssue> issues) {
        return new FileReadResult(
                records,
                new FileReadReport(
                        descriptor.logicalFileName(),
                        descriptor.format(),
                        encoding,
                        headerStatus,
                        rowCount,
                        okCount,
                        warningCount,
                        errorCount,
                        skippedCount,
                        issues));
    }

    private record DecodedInput(Reader reader, String encoding) {
    }

    private record CsvRow(List<String> values, int startLine) {
    }

    private static final class CsvParseException extends IOException {
        private final int lineNumber;

        private CsvParseException(int lineNumber, String message) {
            super(message);
            this.lineNumber = lineNumber;
        }
    }

    private static final class CsvRecordReader {
        private final Reader reader;
        private int lineNumber = 1;
        private int pending = Integer.MIN_VALUE;
        private boolean eof;

        private CsvRecordReader(Reader reader) {
            this.reader = reader;
        }

        private CsvRow next() throws IOException {
            if (eof) {
                return null;
            }
            List<String> values = new ArrayList<>();
            StringBuilder value = new StringBuilder();
            boolean quoted = false;
            boolean started = false;
            int startLine = lineNumber;
            while (true) {
                int codePoint = readCharacter();
                if (codePoint == -1) {
                    eof = true;
                    if (!started && value.isEmpty() && values.isEmpty()) {
                        return null;
                    }
                    if (quoted) {
                        throw new CsvParseException(startLine, "Unclosed quoted CSV field");
                    }
                    values.add(value.toString());
                    return new CsvRow(values, startLine);
                }
                char current = (char) codePoint;
                started = true;
                if (quoted) {
                    if (current == '"') {
                        int next = readCharacter();
                        if (next == '"') {
                            value.append('"');
                        } else {
                            quoted = false;
                            if (next != -1) {
                                pending = next;
                            }
                        }
                    } else {
                        if (current == '\n') {
                            lineNumber++;
                        }
                        value.append(current);
                    }
                    continue;
                }
                if (current == '"' && value.isEmpty()) {
                    quoted = true;
                } else if (current == ',') {
                    values.add(value.toString());
                    value.setLength(0);
                } else if (current == '\n') {
                    String finalValue = value.toString();
                    if (finalValue.endsWith("\r")) {
                        finalValue = finalValue.substring(0, finalValue.length() - 1);
                    }
                    values.add(finalValue);
                    lineNumber++;
                    return new CsvRow(values, startLine);
                } else {
                    value.append(current);
                }
            }
        }

        private int readCharacter() throws IOException {
            if (pending != Integer.MIN_VALUE) {
                int current = pending;
                pending = Integer.MIN_VALUE;
                return current;
            }
            return reader.read();
        }
    }

    private static final class CharsetDecoderReader extends Reader {
        private final Reader delegate;

        private CharsetDecoderReader(InputStream input, Charset charset) {
            this.delegate = new InputStreamReader(
                    input,
                    charset.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT));
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
