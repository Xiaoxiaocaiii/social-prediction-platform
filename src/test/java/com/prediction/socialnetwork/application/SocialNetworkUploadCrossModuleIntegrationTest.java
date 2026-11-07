package com.prediction.socialnetwork.application;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocialNetworkUploadCrossModuleIntegrationTest {

    private static final String DATASET_API = "/api/social-network/datasets/";
    private static final String BUILTIN_DATASET_ID = "think-tank-social-network";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SocialNetworkUploadTaskService uploadTasks;

    @Autowired
    private SocialNetworkDatasetRegistry registry;

    @Autowired
    private SocialNetworkWorkspaceService workspace;

    @Test
    void supportedFileCombinationsShareOneContextAcrossAnalysisModules() {
        List<Scenario> scenarios = List.of(
                new Scenario("relationships-only", List.of(relationships("relations-only")),
                        "available", "unavailable", false, true, null),
                new Scenario("relationships-and-users",
                        List.of(relationships("relations-users"), users("relations-users")),
                        "available", "unavailable", false, true, null),
                new Scenario("relationships-and-tweets",
                        List.of(relationships("relations-tweets"), tweets("relations-tweets")),
                        "available", "limited", true, true, "crossmodule-relations-tweets"),
                new Scenario("users-and-tweets",
                        List.of(users("users-tweets"), tweets("users-tweets")),
                        "unavailable", "available", true, false, "crossmodule-users-tweets"),
                new Scenario("all-three-roles",
                        List.of(users("all"), relationships("all"), tweets("all")),
                        "available", "available", true, true, "crossmodule-all"));

        for (Scenario scenario : scenarios) {
            SocialNetworkUploadTaskView.Preview preview = uploadReady(scenario.files());
            try {
                JsonNode capabilities = getData(
                        preview, "capabilities").path("items");
                assertEquals(scenario.expectedNetworkCapability(),
                        capabilityStatus(capabilities, "network-overview"),
                        scenario.name() + " network capability");
                assertEquals(scenario.expectedInteractionCapability(),
                        capabilityStatus(capabilities, "tweet-interaction"),
                        scenario.name() + " interaction capability");
                assertEquals(scenario.hasTweets() ? "available" : "unavailable",
                        capabilityStatus(capabilities, "topic-analysis"),
                        scenario.name() + " topic capability");

                JsonNode network = getData(preview, "network/summary");
                assertEquals(scenario.hasRelationships() ? 1 : 0,
                        network.path("edgeCount").asInt(),
                        scenario.name() + " network data");

                JsonNode interactions = getData(preview, "interactions/summary");
                assertEquals(scenario.hasTweets() ? "available" : "empty",
                        interactions.path("state").asText(),
                        scenario.name() + " interaction data");
                if (scenario.hasTweets()) {
                    JsonNode interactionTweets = getData(
                            preview, "interactions/tweets?metric=like_count&limit=10");
                    assertEquals(scenario.expectedTweetId(),
                            interactionTweets.path("items").path(0).path("tweetId").asText());
                }

                JsonNode text = getData(preview, "text/summary");
                assertEquals(scenario.hasTweets() ? "available" : "empty",
                        text.path("state").asText(),
                        scenario.name() + " topic data");
                JsonNode textSearch = getData(preview, "text/search?query=researchsignal&limit=10");
                if (scenario.hasTweets()) {
                    assertEquals(scenario.expectedTweetId(),
                            textSearch.path("items").path(0).path("tweetId").asText());
                } else {
                    assertEquals(0, textSearch.path("items").size());
                }
            } finally {
                close(preview);
            }
        }
    }

    @Test
    void failedAndClosedUploadContextsDoNotChangeBuiltinModuleResults() {
        BuiltinSnapshot beforeFailure = builtinSnapshot();
        SocialNetworkUploadTaskView failed = submit(List.of(
                new UploadPart(
                        "invalid-users", "users", "invalid-users.csv",
                        "username\nmissing-id\n")));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertEquals(
                        "failed", uploadTasks.getTask(failed.uploadTaskId()).status()));
        assertBuiltinSnapshot(beforeFailure);

        SocialNetworkUploadTaskView.Preview preview = uploadReady(List.of(
                users("close-session"), relationships("close-session"), tweets("close-session")));
        assertTrue(getData(preview, "network/summary").path("edgeCount").asInt() > 0);
        assertEquals("available",
                getData(preview, "interactions/summary").path("state").asText());
        assertEquals("available",
                getData(preview, "text/summary").path("state").asText());
        close(preview);

        assertSessionExpired(preview, "network/summary");
        assertSessionExpired(preview, "interactions/summary");
        assertSessionExpired(preview, "text/summary");
        assertBuiltinSnapshot(beforeFailure);
    }

    @Test
    void sourceDescriptionIsOptionalAndExistsOnlyForTheUploadSession() {
        SocialNetworkUploadTaskView.Preview omittedSource = uploadReady(List.of(
                users("source-omitted"), relationships("source-omitted")));
        assertEquals("用户未提供",
                registry.findDataset(omittedSource.datasetId()).sourceDescription());
        close(omittedSource);
        assertThrows(IllegalArgumentException.class,
                () -> registry.findDataset(omittedSource.datasetId()));

        String sourceDescription = "user-provided source note";
        SocialNetworkUploadTaskView.Preview suppliedSource = uploadReady(
                List.of(users("source-provided"), relationships("source-provided")),
                sourceDescription);
        assertEquals(sourceDescription,
                registry.findDataset(suppliedSource.datasetId()).sourceDescription());
        close(suppliedSource);
        assertThrows(IllegalArgumentException.class,
                () -> registry.findDataset(suppliedSource.datasetId()));
    }

    @Test
    void concurrentMultipartUploadsKeepTheirSessionsIsolated() throws Exception {
        CountDownLatch clientsReady = new CountDownLatch(2);
        CountDownLatch startClients = new CountDownLatch(1);
        ExecutorService clients = Executors.newFixedThreadPool(2);
        List<UploadPart> firstFiles = List.of(
                new UploadPart(
                        "users-parallel-a", "users", "users-parallel-a.csv",
                        "user_id,username\nparallel-a-1,alpha\nparallel-a-2,beta\n"),
                new UploadPart(
                        "relationships-parallel-a", "relationships",
                        "relationships-parallel-a.csv",
                        "source_user_id,target_user_id\nparallel-a-1,parallel-a-2\n"));
        List<UploadPart> secondFiles = List.of(
                new UploadPart(
                        "users-parallel-b", "users", "users-parallel-b.csv",
                        "user_id,username\nparallel-b-1,gamma\nparallel-b-2,delta\n"),
                new UploadPart(
                        "relationships-parallel-b", "relationships",
                        "relationships-parallel-b.csv",
                        "source_user_id,target_user_id\nparallel-b-1,parallel-b-2\n"));
        CompletableFuture<SocialNetworkUploadTaskView.Preview> first = CompletableFuture.supplyAsync(
                () -> uploadAfterBarrier(firstFiles, clientsReady, startClients), clients);
        CompletableFuture<SocialNetworkUploadTaskView.Preview> second = CompletableFuture.supplyAsync(
                () -> uploadAfterBarrier(secondFiles, clientsReady, startClients), clients);
        SocialNetworkUploadTaskView.Preview firstPreview = null;
        SocialNetworkUploadTaskView.Preview secondPreview = null;
        try {
            assertTrue(clientsReady.await(5, TimeUnit.SECONDS));
            startClients.countDown();
            CompletableFuture.allOf(first, second).join();
            firstPreview = first.join();
            secondPreview = second.join();

            assertNotEquals(firstPreview.datasetId(), secondPreview.datasetId());
            SocialNetworkWorkspaceSnapshot firstSnapshot = workspace.get(
                    firstPreview.datasetId(),
                    firstPreview.datasetVersion(),
                    firstPreview.sessionId());
            SocialNetworkWorkspaceSnapshot secondSnapshot = workspace.get(
                    secondPreview.datasetId(),
                    secondPreview.datasetVersion(),
                    secondPreview.sessionId());
            assertEquals(Set.of("parallel-a-1", "parallel-a-2"),
                    firstSnapshot.users().stream().map(user -> user.userId()).collect(
                            java.util.stream.Collectors.toSet()));
            assertEquals(Set.of("parallel-b-1", "parallel-b-2"),
                    secondSnapshot.users().stream().map(user -> user.userId()).collect(
                            java.util.stream.Collectors.toSet()));
            assertEquals("parallel-a-2", firstSnapshot.relationships().get(0).targetUserId());
            assertEquals("parallel-b-2", secondSnapshot.relationships().get(0).targetUserId());
        } finally {
            startClients.countDown();
            clients.shutdownNow();
            if (firstPreview == null && first.isDone() && !first.isCompletedExceptionally()) {
                firstPreview = first.getNow(null);
            }
            if (secondPreview == null && second.isDone() && !second.isCompletedExceptionally()) {
                secondPreview = second.getNow(null);
            }
            if (firstPreview != null) {
                close(firstPreview);
            }
            if (secondPreview != null) {
                close(secondPreview);
            }
        }
    }

    private SocialNetworkUploadTaskView.Preview uploadAfterBarrier(
            List<UploadPart> files, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent upload start timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent upload interrupted", exception);
        }
        return uploadReady(files);
    }

    private SocialNetworkUploadTaskView.Preview uploadReady(List<UploadPart> files) {
        return uploadReady(files, null);
    }

    private SocialNetworkUploadTaskView.Preview uploadReady(
            List<UploadPart> files, String sourceDescription) {
        SocialNetworkUploadTaskView task = submit(files, sourceDescription);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertEquals(
                        "ready", uploadTasks.getTask(task.uploadTaskId()).status()));
        SocialNetworkUploadTaskView.Preview preview =
                uploadTasks.getTask(task.uploadTaskId()).preview();
        if (sourceDescription == null) {
            assertEquals("用户未提供",
                    registry.findDataset(preview.datasetId()).sourceDescription());
        }
        return preview;
    }

    private SocialNetworkUploadTaskView submit(List<UploadPart> files) {
        return submit(files, null);
    }

    private SocialNetworkUploadTaskView submit(
            List<UploadPart> files, String sourceDescription) {
        String sourceField = sourceDescription == null
                ? ""
                : ",\"sourceDescription\":\"" + sourceDescription + "\"";
        String manifest = "{\"files\":["
                + String.join(",", files.stream()
                        .map(file -> "{\"partId\":\"" + file.partId()
                                + "\",\"role\":\"" + file.role() + "\"}")
                        .toList())
                + "]" + sourceField + "}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                DATASET_API + "upload",
                new HttpEntity<>(multipartBody(manifest, files), headers),
                JsonNode.class);

        assertEquals(202, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("SN_UPLOAD_ACCEPTED", response.getBody().path("code").asText());
        return uploadTasks.getTask(
                response.getBody().path("data").path("uploadTaskId").asText());
    }

    private JsonNode getData(SocialNetworkUploadTaskView.Preview preview, String resource) {
        String path = DATASET_API + preview.datasetId() + "/" + resource;
        String separator = resource.contains("?") ? "&" : "?";
        String url = path + separator
                + "datasetVersion=" + encode(preview.datasetVersion())
                + "&sessionId=" + encode(preview.sessionId());
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);

        assertEquals(200, response.getStatusCode().value(), resource);
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().path("success").asBoolean());
        assertEquals(preview.datasetId(), response.getBody().path("meta").path("datasetId").asText());
        assertEquals(
                preview.datasetVersion(),
                response.getBody().path("meta").path("datasetVersion").asText());
        return response.getBody().path("data");
    }

    private BuiltinSnapshot builtinSnapshot() {
        JsonNode network = getBuiltinData("network/summary");
        JsonNode interactions = getBuiltinData("interactions/summary");
        JsonNode text = getBuiltinData("text/summary");
        return new BuiltinSnapshot(network, interactions, text);
    }

    private JsonNode getBuiltinData(String resource) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                DATASET_API + BUILTIN_DATASET_ID + "/" + resource, JsonNode.class);
        assertEquals(200, response.getStatusCode().value(), resource);
        assertNotNull(response.getBody());
        assertEquals(BUILTIN_DATASET_ID,
                response.getBody().path("meta").path("datasetId").asText());
        return response.getBody().path("data");
    }

    private void assertBuiltinSnapshot(BuiltinSnapshot expected) {
        assertEquals(expected.network(), getBuiltinData("network/summary"));
        assertEquals(expected.interactions(), getBuiltinData("interactions/summary"));
        assertEquals(expected.text(), getBuiltinData("text/summary"));
    }

    private void assertSessionExpired(
            SocialNetworkUploadTaskView.Preview preview, String resource) {
        String path = DATASET_API + preview.datasetId() + "/" + resource
                + "?datasetVersion=" + encode(preview.datasetVersion())
                + "&sessionId=" + encode(preview.sessionId());
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(path, JsonNode.class);

        assertEquals(410, response.getStatusCode().value(), resource);
        assertNotNull(response.getBody());
        assertEquals("SN_DATASET_SESSION_EXPIRED", response.getBody().path("code").asText());
    }

    private void close(SocialNetworkUploadTaskView.Preview preview) {
        uploadTasks.closeSession(
                preview.datasetId(), preview.datasetVersion(), preview.sessionId());
    }

    private static String capabilityStatus(JsonNode items, String capabilityId) {
        for (JsonNode item : items) {
            if (capabilityId.equals(item.path("capabilityId").asText())) {
                return item.path("status").asText().toLowerCase(Locale.ROOT);
            }
        }
        throw new AssertionError("Missing capability: " + capabilityId);
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static MultiValueMap<String, Object> multipartBody(
            String manifest, List<UploadPart> files) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("manifest", new HttpEntity<>(manifest, jsonPartHeaders()));
        for (UploadPart file : files) {
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            fileHeaders.setContentDisposition(
                    ContentDisposition.formData().name(file.partId())
                            .filename(file.fileName()).build());
            body.add(file.partId(), new HttpEntity<>(new ByteArrayResource(
                    file.contents().getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getFilename() {
                    return file.fileName();
                }
            }, fileHeaders));
        }
        return body;
    }

    private static HttpHeaders jsonPartHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.formData().name("manifest").build());
        return headers;
    }

    private static UploadPart users(String suffix) {
        return new UploadPart(
                "users-" + suffix,
                "users",
                "users-" + suffix + ".csv",
                "user_id,username\nuser-a,alpha\nuser-b,beta\n");
    }

    private static UploadPart relationships(String suffix) {
        return new UploadPart(
                "relationships-" + suffix,
                "relationships",
                "relationships-" + suffix + ".csv",
                "source_user_id,target_user_id\nuser-a,user-b\n");
    }

    private static UploadPart tweets(String suffix) {
        return new UploadPart(
                "tweets-" + suffix,
                "tweets",
                "tweets-" + suffix + ".csv",
                "tweet_id,user_id,created_at,text,like_count,reply_count,repost_count\n"
                        + "crossmodule-" + suffix
                        + ",user-a,2026-10-16,researchsignal evidence,7,2,1\n");
    }

    private record UploadPart(String partId, String role, String fileName, String contents) {
    }

    private record Scenario(
            String name,
            List<UploadPart> files,
            String expectedNetworkCapability,
            String expectedInteractionCapability,
            boolean hasTweets,
            boolean hasRelationships,
            String expectedTweetId) {
    }

    private record BuiltinSnapshot(JsonNode network, JsonNode interactions, JsonNode text) {
    }
}
