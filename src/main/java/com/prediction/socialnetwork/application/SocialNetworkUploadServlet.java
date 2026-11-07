package com.prediction.socialnetwork.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SocialNetworkUploadServlet extends HttpServlet {

    private static final int MAX_FILES = 10;
    private static final long MAX_FILE_BYTES = 128L * 1024 * 1024;
    private static final long MAX_REQUEST_BYTES = 160L * 1024 * 1024;
    private static final long MAX_MANIFEST_BYTES = 1024L * 1024;

    private final SocialNetworkUploadTaskService uploadTasks;
    private final SocialNetworkUploadTemporaryFileCleanup temporaryFileCleanup;
    private final ObjectMapper objectMapper;
    private final Path requestStagingRoot;

    public SocialNetworkUploadServlet(
            SocialNetworkUploadTaskService uploadTasks,
            SocialNetworkUploadTemporaryFileCleanup temporaryFileCleanup,
            ObjectMapper objectMapper,
            Path requestStagingRoot) {
        this.uploadTasks = uploadTasks;
        this.temporaryFileCleanup = temporaryFileCleanup;
        this.objectMapper = objectMapper;
        this.requestStagingRoot = requestStagingRoot;
        this.temporaryFileCleanup.registerManagedRoot(requestStagingRoot, "request-");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String requestId = UUID.randomUUID().toString();
        List<Part> parts = List.of();
        Path requestDirectory = null;
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            writeError(response, requestId, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "SN_BAD_REQUEST", "上传请求超过允许的大小限制。");
            return;
        }
        try {
            parts = readParts(request);
            UploadManifest manifest = parseManifest(parts);
            Map<String, Part> fileParts = matchParts(parts, manifest);
            List<SocialNetworkUploadFileInput> inputs;
            try {
                Files.createDirectories(requestStagingRoot);
                requestDirectory = Files.createTempDirectory(requestStagingRoot, "request-");
                temporaryFileCleanup.markActiveDirectory(requestDirectory);
                inputs = copyParts(manifest, fileParts, requestDirectory);
            } catch (IOException exception) {
                writeError(response, requestId, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "SN_INTERNAL_ERROR", "上传文件暂存失败，请稍后重试。");
                return;
            }

            SocialNetworkUploadTaskView task =
                    uploadTasks.submit(inputs, manifest.sourceDescription());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uploadTaskId", task.uploadTaskId());
            data.put("status", task.status());
            data.put("statusUrl", "/api/social-network/datasets/upload-tasks/"
                    + task.uploadTaskId());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("datasetId", null);
            meta.put("datasetVersion", null);
            meta.put("scope", Map.of());
            writeResponse(response, HttpServletResponse.SC_ACCEPTED, requestId,
                    new SocialNetworkApiResponse<>(
                            true,
                            "SN_UPLOAD_ACCEPTED",
                            "上传已接收，正在检查数据",
                            requestId,
                            data,
                            List.of(),
                            meta));
        } catch (UploadRequestException exception) {
            writeError(response, requestId, exception.status(), exception.code(), exception.getMessage());
        } catch (IllegalArgumentException exception) {
            writeError(response, requestId, HttpServletResponse.SC_BAD_REQUEST,
                    "SN_BAD_REQUEST", "上传文件信息无效，请检查文件名称和格式。");
        } catch (Exception exception) {
            writeError(response, requestId, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "SN_INTERNAL_ERROR", "上传请求处理失败，请稍后重试。");
        } finally {
            for (Part part : parts) {
                try {
                    part.delete();
                } catch (IOException ignored) {
                    // Container cleanup remains a fallback for failed part deletion.
                }
            }
            temporaryFileCleanup.deleteManagedDirectory(requestDirectory);
        }
    }

    private static List<Part> readParts(HttpServletRequest request) {
        try {
            return new ArrayList<>(request.getParts());
        } catch (IllegalStateException | ServletException exception) {
            if (isSizeLimitFailure(exception)) {
                throw new UploadRequestException(
                        HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "SN_BAD_REQUEST",
                        "上传请求超过允许的大小限制。");
            }
            throw partMismatch();
        } catch (IOException exception) {
            throw new UploadRequestException(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "SN_INTERNAL_ERROR",
                    "上传请求解析失败，请稍后重试。");
        }
    }

    private UploadManifest parseManifest(List<Part> parts) throws IOException {
        Part manifestPart = null;
        for (Part part : parts) {
            if ("manifest".equals(part.getName())) {
                if (manifestPart != null) {
                    throw partMismatch();
                }
                manifestPart = part;
            }
        }
        if (manifestPart == null || manifestPart.getSize() > MAX_MANIFEST_BYTES) {
            throw manifestInvalid();
        }

        JsonNode root;
        try (var input = manifestPart.getInputStream()) {
            root = objectMapper.readTree(input);
        } catch (IOException exception) {
            throw manifestInvalid();
        }
        if (root == null || !root.isObject()
                || !root.has("files") || !root.get("files").isArray()) {
            throw manifestInvalid();
        }
        JsonNode filesNode = root.get("files");
        if (filesNode.isEmpty() || filesNode.size() > MAX_FILES) {
            throw manifestInvalid();
        }
        String sourceDescription = null;
        JsonNode sourceNode = root.get("sourceDescription");
        if (sourceNode != null && !sourceNode.isNull()) {
            if (!sourceNode.isTextual()) {
                throw manifestInvalid();
            }
            sourceDescription = sourceNode.asText();
        }

        List<ManifestFile> files = new ArrayList<>();
        Set<String> partIds = new HashSet<>();
        for (JsonNode fileNode : filesNode) {
            if (!fileNode.isObject()
                    || !fileNode.has("partId")
                    || !fileNode.get("partId").isTextual()
                    || !fileNode.has("role")
                    || !fileNode.get("role").isTextual()) {
                throw manifestInvalid();
            }
            String partId = fileNode.get("partId").asText();
            DatasetFileRole role = roleOf(fileNode.get("role").asText());
            if (partId.isBlank() || !partIds.add(partId)) {
                throw manifestInvalid();
            }
            files.add(new ManifestFile(partId, role));
        }
        return new UploadManifest(List.copyOf(files), sourceDescription);
    }

    private static Map<String, Part> matchParts(
            List<Part> parts, UploadManifest manifest) {
        Map<String, Part> files = new LinkedHashMap<>();
        for (Part part : parts) {
            if ("manifest".equals(part.getName())) {
                continue;
            }
            if (part.getSubmittedFileName() == null
                    || files.putIfAbsent(part.getName(), part) != null) {
                throw partMismatch();
            }
        }
        if (files.size() != manifest.files().size()
                || !files.keySet().containsAll(manifest.files().stream()
                        .map(ManifestFile::partId)
                        .toList())) {
            throw partMismatch();
        }
        return files;
    }

    private static List<SocialNetworkUploadFileInput> copyParts(
            UploadManifest manifest,
            Map<String, Part> fileParts,
            Path requestDirectory) throws IOException {
        List<SocialNetworkUploadFileInput> inputs = new ArrayList<>();
        long requestBytes = 0L;
        for (int index = 0; index < manifest.files().size(); index++) {
            ManifestFile file = manifest.files().get(index);
            Part part = fileParts.get(file.partId());
            long size = part.getSize();
            if (size > MAX_FILE_BYTES) {
                throw new UploadRequestException(
                        HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "SN_BAD_REQUEST",
                        "上传文件超过允许的大小限制。");
            }
            requestBytes = Math.addExact(requestBytes, size);
            if (requestBytes > MAX_REQUEST_BYTES) {
                throw new UploadRequestException(
                        HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "SN_BAD_REQUEST",
                        "上传请求超过允许的大小限制。");
            }
            String fileName = part.getSubmittedFileName();
            if (!isSupportedFileName(fileName)) {
                throw new UploadRequestException(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "SN_UNSUPPORTED_FORMAT",
                        "仅支持 CSV 和 JSONL 文件。");
            }
            Path stagedFile = requestDirectory.resolve(String.format(
                    Locale.ROOT, "part-%02d", index + 1));
            try (var input = part.getInputStream()) {
                Files.copy(input, stagedFile);
            }
            inputs.add(new SocialNetworkUploadFileInput(
                    file.partId(), fileName, file.role(), stagedFile));
        }
        return List.copyOf(inputs);
    }

    private static DatasetFileRole roleOf(String value) {
        return switch (value) {
            case "users" -> DatasetFileRole.USERS;
            case "relationships" -> DatasetFileRole.RELATIONSHIPS;
            case "tweets" -> DatasetFileRole.TWEETS;
            default -> throw manifestInvalid();
        };
    }

    private static boolean isSupportedFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".csv") || normalized.endsWith(".jsonl");
    }

    private static boolean isSizeLimitFailure(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("size")
                        || normalized.contains("exceed")
                        || normalized.contains("limit")
                        || normalized.contains("too large")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void writeError(
            HttpServletResponse response,
            String requestId,
            int status,
            String code,
            String message) throws IOException {
        writeResponse(
                response,
                status,
                requestId,
                SocialNetworkApiResponse.error(
                        requestId, code, message, errorMeta()));
    }

    private void writeResponse(
            HttpServletResponse response,
            int status,
            String requestId,
            SocialNetworkApiResponse<?> body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("X-Request-Id", requestId);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static Map<String, Object> errorMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("datasetId", null);
        meta.put("datasetVersion", null);
        meta.put("scope", Map.of());
        return meta;
    }

    private static UploadRequestException manifestInvalid() {
        return new UploadRequestException(
                HttpServletResponse.SC_BAD_REQUEST,
                "SN_UPLOAD_MANIFEST_INVALID",
                "上传清单无效，请检查文件角色和清单格式。");
    }

    private static UploadRequestException partMismatch() {
        return new UploadRequestException(
                HttpServletResponse.SC_BAD_REQUEST,
                "SN_UPLOAD_PART_MISMATCH",
                "上传清单与实际文件部分不一致。");
    }

    private record ManifestFile(String partId, DatasetFileRole role) {
    }

    private record UploadManifest(List<ManifestFile> files, String sourceDescription) {
    }

    private static final class UploadRequestException extends RuntimeException {
        private final int status;
        private final String code;

        private UploadRequestException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        private int status() {
            return status;
        }

        private String code() {
            return code;
        }
    }
}
