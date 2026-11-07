package com.prediction.socialnetwork.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.SourceFormat;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SocialNetworkFileReaderTest {

    private final SocialNetworkFileReader reader = new SocialNetworkFileReader();

    @Test
    void readsBuiltinCsvAndMapsChineseHeadersWithoutChangingIds() {
        String csv = "账号ID,用户名,粉丝数\n1002259603537702912,niwcrso,1475\n";

        FileReadResult result = reader.read(
                bytes(csv),
                new DatasetFileDescriptor("users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8"),
                true);

        assertEquals(HeaderStatus.VALID, result.report().headerStatus());
        assertEquals(1, result.records().size());
        assertEquals("1002259603537702912", result.records().get(0).fields().get("user_id"));
        assertEquals(2, result.records().get(0).sourceRowNumber());
    }

    @Test
    void readsStandardJsonlAndReportsInvalidLineNumber() {
        String jsonl = """
                {"tweet_id":"9007199254740993123","user_id":"42","created_at":"2025-01-01","text":"hello"}
                not-json
                """;

        FileReadResult result = reader.read(
                bytes(jsonl),
                new DatasetFileDescriptor("tweets.jsonl", DatasetFileRole.TWEETS, SourceFormat.JSONL, "utf8"),
                false);

        assertEquals(1, result.records().size());
        assertEquals("9007199254740993123", result.records().get(0).fields().get("tweet_id"));
        assertEquals(1, result.report().readOkCount());
        assertEquals(1, result.report().readErrorCount());
        assertEquals(2, result.report().issues().get(0).sourceRowNumber());
    }

    @Test
    void parsesFollowingListAndKeepsEmptyListEmpty() {
        assertEquals(List.of("188736341", "3912832153"),
                SocialNetworkFileReader.parseFollowingList("['188736341', '3912832153']"));
        assertEquals(List.of(), SocialNetworkFileReader.parseFollowingList("[]"));
        assertEquals(List.of(), SocialNetworkFileReader.parseFollowingList(null));
        assertEquals(List.of(), SocialNetworkFileReader.parseFollowingList("  "));
        assertThrows(IllegalArgumentException.class,
                () -> SocialNetworkFileReader.parseFollowingList("188736341"));
    }

    @Test
    void acceptsUtf8Bom() {
        byte[] body = "user_id,username\n001,a\n".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);

        FileReadResult result = reader.read(
                new ByteArrayInputStream(withBom),
                new DatasetFileDescriptor("users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8_bom"),
                false);

        assertEquals("utf8_bom", result.report().encoding());
        assertEquals("001", result.records().get(0).fields().get("user_id"));
    }

    @Test
    void reportsEmptyCsvAsUnusableWithoutInventingRows() {
        FileReadResult result = reader.read(
                bytes(""),
                new DatasetFileDescriptor("empty.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8"),
                false);

        assertEquals(0, result.records().size());
        assertEquals(0, result.report().readOkCount());
        assertEquals(0, result.report().readErrorCount());
        org.junit.jupiter.api.Assertions.assertFalse(result.report().isUsable());
    }

    @Test
    void sanitizesJsonlParserMessagesInsteadOfEchoingInput() {
        String secret = "PRIVATE-CONTENT-7f31";
        String jsonl = "{\"tweet_id\":\"tweet-1\",\"user_id\":\"u1\",\"created_at\":\"2025\","
                + "\"text\":\"valid\"}\n{\"text\":\"" + secret;

        FileReadResult result = reader.read(
                bytes(jsonl),
                new DatasetFileDescriptor("tweets.jsonl", DatasetFileRole.TWEETS, SourceFormat.JSONL, "utf8"),
                false);

        FileReadIssue issue = result.report().issues().stream()
                .filter(candidate -> "JSONL_PARSE_FAILED".equals(candidate.code()))
                .findFirst()
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(issue.message().contains(secret));
        assertEquals("该行不是有效的 JSONL 对象", issue.message());
    }

    @Test
    void malformedUtf8InvalidatesTheWholeFileEvenAfterValidRows() {
        byte[] validPrefix = "user_id\nu1\n".getBytes(StandardCharsets.UTF_8);
        byte[] malformed = java.util.Arrays.copyOf(validPrefix, validPrefix.length + 2);
        malformed[validPrefix.length] = (byte) 0xC3;
        malformed[validPrefix.length + 1] = (byte) 0x28;

        FileReadResult result = reader.read(
                new ByteArrayInputStream(malformed),
                new DatasetFileDescriptor("users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8"),
                false);

        assertEquals(HeaderStatus.INVALID, result.report().headerStatus());
        assertEquals(1, result.report().readErrorCount());
        org.junit.jupiter.api.Assertions.assertFalse(result.report().isUsable());
        assertEquals("UNSUPPORTED_ENCODING", result.report().issues().get(0).code());
    }

    @Test
    void rejectsUtf16BomWithAStableEncodingError() {
        byte[] utf16 = "user_id\nu1\n".getBytes(StandardCharsets.UTF_16);

        FileReadResult result = reader.read(
                new ByteArrayInputStream(utf16),
                new DatasetFileDescriptor("users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8"),
                false);

        assertEquals(HeaderStatus.INVALID, result.report().headerStatus());
        assertEquals("UNSUPPORTED_ENCODING", result.report().issues().get(0).code());
        assertEquals("文件编码无效，请使用 UTF-8 或 UTF-8 BOM",
                result.report().issues().get(0).message());
    }

    @Test
    void malformedCsvAfterValidRowsBlocksAndDiscardsTheWholeFile() {
        String secret = "PRIVATE-CSV-CONTENT-4ac2";
        String csv = "user_id,username\nvalid-user,valid\nbroken-user,\"" + secret;

        FileReadResult result = reader.read(
                bytes(csv),
                new DatasetFileDescriptor("users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8"),
                false);

        assertEquals(HeaderStatus.INVALID, result.report().headerStatus());
        assertEquals(0, result.report().readOkCount());
        assertEquals(0, result.records().size());
        assertEquals("CSV_PARSE_FAILED", result.report().issues().get(0).code());
        assertEquals(3, result.report().issues().get(0).sourceRowNumber());
        assertEquals("CSV 内容格式无效，无法继续读取该文件",
                result.report().issues().get(0).message());
        assertFalse(result.report().issues().get(0).message().contains(secret));
    }

    @Test
    void rejectsUnexpectedCharactersAfterAQuotedCsvField() {
        FileReadResult result = reader.read(
                bytes("user_id,username\nuser-1,\"valid\"unexpected\n"),
                new DatasetFileDescriptor("users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8"),
                false);

        assertEquals(HeaderStatus.INVALID, result.report().headerStatus());
        assertEquals(0, result.records().size());
        assertEquals("CSV_PARSE_FAILED", result.report().issues().get(0).code());
    }

    private static ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
