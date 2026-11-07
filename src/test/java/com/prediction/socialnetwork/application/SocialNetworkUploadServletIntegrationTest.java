package com.prediction.socialnetwork.application;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.env.Environment;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocialNetworkUploadServletIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SocialNetworkUploadTaskService uploadTasks;

    @Autowired
    private SocialNetworkWorkspaceService workspace;

    @Autowired
    private Environment environment;

    @Test
    void dedicatedServletAcceptsOverMvcFileLimitAndMatchesManifestByPartId() {
        String partId = "user-" + UUID.randomUUID();
        String content = "user_id,extra\nuser-1," + "x".repeat(10 * 1024 * 1024) + "\n";
        MultiValueMap<String, Object> body = multipartBody(
                """
                {"files":[{"partId":"%s","role":"users"}],"sourceDescription":"integration test"}
                """.formatted(partId),
                new UploadPart(partId, "users.csv", content.getBytes(StandardCharsets.UTF_8)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/api/social-network/datasets/upload",
                new HttpEntity<>(body, headers),
                JsonNode.class);

        assertEquals(202, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().path("success").asBoolean());
        assertEquals("SN_UPLOAD_ACCEPTED", response.getBody().path("code").asText());
        assertNotNull(response.getBody().path("data").path("uploadTaskId").textValue());
        String taskId = response.getBody().path("data").path("uploadTaskId").asText();

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertEquals("ready", uploadTasks.getTask(taskId).status()));
        SocialNetworkUploadTaskView task = uploadTasks.getTask(taskId);
        uploadTasks.closeSession(
                task.preview().datasetId(),
                task.preview().datasetVersion(),
                task.preview().sessionId());
    }

    @Test
    void manifestPartMismatchReturnsTheSocialNetworkErrorEnvelope() {
        MultiValueMap<String, Object> body = multipartBody(
                "{\"files\":[{\"partId\":\"expected\",\"role\":\"users\"}]}",
                new UploadPart(
                        "actual", "users.csv", "user_id\nuser-1\n".getBytes(StandardCharsets.UTF_8)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/api/social-network/datasets/upload",
                new HttpEntity<>(body, headers),
                JsonNode.class);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().path("success").asBoolean());
        assertEquals("SN_UPLOAD_PART_MISMATCH", response.getBody().path("code").asText());
        assertNotNull(response.getBody().path("requestId").textValue());
    }

    @Test
    void acceptsMultipleFilesWithTheSameRoleAndMergesTheirRecords() {
        String firstPart = "users-a-" + UUID.randomUUID();
        String secondPart = "users-b-" + UUID.randomUUID();
        String manifest = """
                {"files":[{"partId":"%s","role":"users"},{"partId":"%s","role":"users"}]}
                """.formatted(firstPart, secondPart);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/api/social-network/datasets/upload",
                new HttpEntity<>(multipartBody(
                        manifest,
                        new UploadPart(
                                firstPart,
                                "first.csv",
                                "user_id,username\nuser-a,first\n".getBytes(StandardCharsets.UTF_8)),
                        new UploadPart(
                                secondPart,
                                "second.csv",
                                "user_id,username\nuser-b,second\n".getBytes(StandardCharsets.UTF_8))),
                        headers),
                JsonNode.class);

        assertEquals(202, response.getStatusCode().value());
        String taskId = response.getBody().path("data").path("uploadTaskId").asText();
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertEquals("ready", uploadTasks.getTask(taskId).status()));
        SocialNetworkUploadTaskView task = uploadTasks.getTask(taskId);
        assertEquals(2, task.preview().files().size());
        assertTrue(task.preview().files().stream()
                .allMatch(file -> file.role().name().equals("USERS")));
        assertEquals(List.of("user-a", "user-b"), workspace.get(
                        task.preview().datasetId(),
                        task.preview().datasetVersion(),
                        task.preview().sessionId()).users().stream()
                .map(user -> user.userId())
                .toList());
        uploadTasks.closeSession(
                task.preview().datasetId(),
                task.preview().datasetVersion(),
                task.preview().sessionId());
    }

    @Test
    void rejectsMissingDuplicateAndUnknownMultipartPartIds() {
        assertUploadError(
                "{\"files\":[{\"partId\":\"missing\",\"role\":\"users\"}]}",
                "SN_UPLOAD_PART_MISMATCH");
        assertUploadError(
                "{\"files\":[{\"role\":\"users\"}]}",
                "SN_UPLOAD_MANIFEST_INVALID",
                new UploadPart(
                        "actual", "users.csv", "user_id\nuser-1\n".getBytes(StandardCharsets.UTF_8)));
        assertUploadError(
                "{\"files\":[{\"partId\":\"same\",\"role\":\"users\"},"
                        + "{\"partId\":\"same\",\"role\":\"users\"}]}",
                "SN_UPLOAD_MANIFEST_INVALID",
                new UploadPart(
                        "same", "users.csv", "user_id\nuser-1\n".getBytes(StandardCharsets.UTF_8)));
        assertUploadError(
                "{\"files\":[{\"partId\":\"expected\",\"role\":\"users\"}]}",
                "SN_UPLOAD_PART_MISMATCH",
                new UploadPart(
                        "expected",
                        "expected.csv",
                        "user_id\nuser-1\n".getBytes(StandardCharsets.UTF_8)),
                new UploadPart(
                        "unknown",
                        "unknown.csv",
                        "user_id\nuser-2\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsUnsupportedFileFormatWithAUnifiedErrorEnvelope() {
        assertUploadError(
                "{\"files\":[{\"partId\":\"part\",\"role\":\"users\"}]}",
                "SN_UNSUPPORTED_FORMAT",
                new UploadPart(
                        "part", "users.txt", "user_id\nuser-1\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void springMultipartLimitsRemainUnchangedForMvcRoutes() {
        assertEquals("10MB", environment.getProperty("spring.servlet.multipart.max-file-size"));
        assertEquals("12MB", environment.getProperty("spring.servlet.multipart.max-request-size"));
    }

    private void assertUploadError(
            String manifest, String expectedCode, UploadPart... files) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/api/social-network/datasets/upload",
                new HttpEntity<>(multipartBody(manifest, files), headers),
                JsonNode.class);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().path("success").asBoolean());
        assertEquals(expectedCode, response.getBody().path("code").asText());
        assertNotNull(response.getBody().path("requestId").textValue());
        assertTrue(response.getBody().path("meta").path("scope").isObject());
    }

    private static MultiValueMap<String, Object> multipartBody(
            String manifest, UploadPart... files) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders manifestHeaders = new HttpHeaders();
        manifestHeaders.setContentType(MediaType.APPLICATION_JSON);
        manifestHeaders.setContentDisposition(
                ContentDisposition.formData().name("manifest").build());
        body.add("manifest", new HttpEntity<>(manifest, manifestHeaders));

        for (UploadPart file : files) {
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            fileHeaders.setContentDisposition(
                    ContentDisposition.formData().name(file.partId()).filename(file.fileName()).build());
            body.add(file.partId(), new HttpEntity<>(new ByteArrayResource(file.contents()) {
                @Override
                public String getFilename() {
                    return file.fileName();
                }
            }, fileHeaders));
        }
        return body;
    }

    private record UploadPart(String partId, String fileName, byte[] contents) {
    }
}
