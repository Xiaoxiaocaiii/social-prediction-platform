package com.prediction.socialnetwork.application;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    @Autowired
    private SocialNetworkAnalysisResultStore resultStore;

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
    void uploadedCsvRunsLouvainAndReusesOnlyItsActiveSessionResult() {
        SocialNetworkUploadTaskView.Preview preview = uploadReady(List.of(
                users("louvain-upload"),
                relationships("louvain-upload")));
        String path = DATASET_API + preview.datasetId() + "/network/louvain"
                + "?datasetVersion=" + encode(preview.datasetVersion())
                + "&sessionId=" + encode(preview.sessionId());
        try {
            ResponseEntity<JsonNode> started = restTemplate.postForEntity(
                    path, null, JsonNode.class);
            assertEquals(202, started.getStatusCode().value());
            assertNotNull(started.getBody());
            assertEquals(preview.datasetId(),
                    started.getBody().path("data").path("datasetId").asText());
            assertEquals(preview.datasetVersion(),
                    started.getBody().path("data").path("datasetVersion").asText());

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                ResponseEntity<JsonNode> status =
                        restTemplate.getForEntity(path, JsonNode.class);
                assertEquals(200, status.getStatusCode().value());
                assertNotNull(status.getBody());
                assertEquals("COMPLETED",
                        status.getBody().path("data").path("state").asText());
                assertTrue(status.getBody().path("data").path("complete").asBoolean());
            });

            ResponseEntity<JsonNode> reusable =
                    restTemplate.getForEntity(path, JsonNode.class);
            JsonNode result = reusable.getBody().path("data");
            assertEquals(2, result.path("inputNodeCount").asInt());
            assertEquals(1, result.path("inputDirectedRelationshipCount").asInt());
            assertTrue(result.path("cacheHit").asBoolean());
            assertEquals(
                    workspace.get(
                                    preview.datasetId(),
                                    preview.datasetVersion(),
                                    preview.sessionId())
                            .inputFingerprint()
                            .dataFingerprint(),
                    result.path("dataFingerprint").asText());

            ResponseEntity<JsonNode> repeatedStart = restTemplate.postForEntity(
                    path, null, JsonNode.class);
            assertEquals(202, repeatedStart.getStatusCode().value());
            assertTrue(repeatedStart.getBody().path("data").path("cacheHit").asBoolean());
        } finally {
            close(preview);
        }
        assertSessionExpired(preview, "network/louvain");
    }

    @Test
    void uploadedCliqueAndMemberEvidenceStayBoundToTheirSessionSnapshots() {
        SocialNetworkUploadTaskView.Preview first = uploadReady(List.of(
                new UploadPart(
                        "users-e07-first", "users", "users-e07-first.csv",
                        "user_id,username\nuser-a,first-alpha\n"
                                + "user-b,first-beta\nuser-c,first-gamma\n"),
                new UploadPart(
                        "relationships-e07-first", "relationships",
                        "relationships-e07-first.csv",
                        "source_user_id,target_user_id\n"
                                + "user-a,user-b\nuser-b,user-a\n"
                                + "user-a,user-c\nuser-c,user-a\n"
                                + "user-b,user-c\nuser-c,user-b\n")));
        SocialNetworkUploadTaskView.Preview second = uploadReady(List.of(
                new UploadPart(
                        "users-e07-second", "users", "users-e07-second.csv",
                        "user_id,username\nuser-a,second-alpha\n"
                                + "user-b,second-beta\nuser-c,second-gamma\n"),
                new UploadPart(
                        "relationships-e07-second", "relationships",
                        "relationships-e07-second.csv",
                        "source_user_id,target_user_id\n"
                                + "user-a,user-b\nuser-b,user-a\n"
                                + "user-a,user-c\nuser-c,user-a\n"
                                + "user-b,user-c\nuser-c,user-b\n")));
        try {
            JsonNode firstClique = runClique(first);
            assertEquals(3, firstClique.path("inputNodeCount").asInt());
            assertEquals("COMPLETED", firstClique.path("state").asText());
            assertTrue(firstClique.path("membershipListComplete").asBoolean());
            assertEquals(first.datasetId(), firstClique.path("datasetId").asText());
            assertEquals(first.datasetVersion(), firstClique.path("datasetVersion").asText());
            assertEquals(first.sessionId(),
                    restTemplate.getForEntity(cliqueUrl(first), JsonNode.class)
                            .getBody().path("meta").path("sessionId").asText());

            JsonNode firstInsights = getData(first, "network/cliques/insights");
            assertTrue(firstInsights.path("available").asBoolean());
            assertEquals("first-alpha",
                    firstInsights.path("names").path("user-a").asText());
            JsonNode firstEvidence = getData(first,
                    "network/cliques/0/evidence?section=members&pageSize=20");
            assertTrue(firstEvidence.path("evidenceAvailable").asBoolean());
            assertEquals(firstClique.path("dataFingerprint").asText(),
                    firstEvidence.path("dataFingerprint").asText());
            assertEquals(Set.of("user-a", "user-b", "user-c"),
                    toStringSet(firstEvidence.path("members"), "nodeId"));
            assertTrue(firstEvidence.findValuesAsText("sourceFileName").stream()
                    .allMatch(name -> name.contains("users-e07-first.csv")));
            String firstCacheKey = firstClique.path("cacheKey").asText();
            assertTrue(resultStore.loadClique(firstCacheKey).isEmpty(),
                    "uploaded Clique results must remain in the session cache");

            ResponseEntity<JsonNode> missingContext = restTemplate.getForEntity(
                    DATASET_API + first.datasetId() + "/network/cliques", JsonNode.class);
            assertEquals(400, missingContext.getStatusCode().value());
            assertEquals("SN_DATASET_CONTEXT_MISMATCH",
                    missingContext.getBody().path("code").asText());
            ResponseEntity<JsonNode> mismatchedContext = restTemplate.getForEntity(
                    cliqueUrl(first.datasetId(), second.datasetVersion(), second.sessionId()),
                    JsonNode.class);
            assertEquals(400, mismatchedContext.getStatusCode().value());
            assertEquals("SN_DATASET_CONTEXT_MISMATCH",
                    mismatchedContext.getBody().path("code").asText());

            JsonNode secondClique = runClique(second);
            assertEquals(3, secondClique.path("inputNodeCount").asInt());
            assertNotEquals(firstClique.path("dataFingerprint").asText(),
                    secondClique.path("dataFingerprint").asText());
            JsonNode secondEvidence = getData(second,
                    "network/cliques/0/evidence?section=members&pageSize=20");
            assertTrue(secondEvidence.path("evidenceAvailable").asBoolean());
            assertTrue(secondEvidence.findValuesAsText("sourceFileName").stream()
                    .allMatch(name -> name.contains("users-e07-second.csv")));
            JsonNode secondInsights = getData(second, "network/cliques/insights");
            assertEquals("second-alpha",
                    secondInsights.path("names").path("user-a").asText());

            JsonNode firstAgain = getData(first, "network/cliques");
            assertEquals(firstCacheKey, firstAgain.path("cacheKey").asText());
            assertTrue(firstAgain.path("cacheHit").asBoolean());
            assertEquals("first-alpha",
                    getData(first, "network/cliques/insights")
                            .path("names").path("user-a").asText());

            JsonNode shared = getData(first,
                    "network/cliques/insights/shared?first=user-a&second=user-b");
            assertTrue(shared.path("available").asBoolean());
            assertEquals(1, shared.path("page").path("totalCount").asInt());
        } finally {
            close(first);
            close(second);
        }
        assertSessionExpired(first, "network/cliques");
        assertSessionExpired(first, "network/cliques/0/evidence?section=members");
        assertSessionExpired(first, "network/cliques/insights");
        assertSessionExpired(second, "network/cliques");
    }

    @Test
    void uploadedCliqueExportsStayBoundToTheirSessionsAndExplainEmptyResults()
            throws Exception {
        SocialNetworkUploadTaskView.Preview first = uploadReady(List.of(
                new UploadPart(
                        "users-e08-first", "users", "users-e08-first.csv",
                        "user_id,username\nuser-a,first-alpha\n"
                                + "user-b,first-beta\nuser-c,first-gamma\n"),
                new UploadPart(
                        "relationships-e08-first", "relationships",
                        "relationships-e08-first.csv",
                        "source_user_id,target_user_id\n"
                                + "user-a,user-b\nuser-b,user-a\n"
                                + "user-a,user-c\nuser-c,user-a\n"
                                + "user-b,user-c\nuser-c,user-b\n")));
        SocialNetworkUploadTaskView.Preview second = uploadReady(List.of(
                new UploadPart(
                        "users-e08-second", "users", "users-e08-second.csv",
                        "user_id,username\nuser-a,second-alpha\n"
                                + "user-b,second-beta\nuser-c,second-gamma\n"),
                new UploadPart(
                        "relationships-e08-second", "relationships",
                        "relationships-e08-second.csv",
                        "source_user_id,target_user_id\n"
                                + "user-a,user-b\nuser-b,user-a\n"
                                + "user-a,user-c\nuser-c,user-a\n"
                                + "user-b,user-c\nuser-c,user-b\n")));
        SocialNetworkUploadTaskView.Preview noCliques = uploadReady(List.of(
                new UploadPart(
                        "users-e08-empty", "users", "users-e08-empty.csv",
                        "user_id,username\nuser-a,empty-alpha\n"
                                + "user-b,empty-beta\nuser-c,empty-gamma\n"),
                new UploadPart(
                        "relationships-e08-empty", "relationships",
                        "relationships-e08-empty.csv",
                        "source_user_id,target_user_id\nuser-a,user-b\n")));
        try {
            Map<String, Object> emptyRequest =
                    cliqueExportRequest("json", null);
            ResponseEntity<JsonNode> notRequested = restTemplate.postForEntity(
                    groupExportUrl(first),
                    new HttpEntity<>(emptyRequest, jsonHeaders()),
                    JsonNode.class);
            assertEquals(422, notRequested.getStatusCode().value());
            assertEquals("SN_EXPORT_EMPTY_RESULT",
                    notRequested.getBody().path("code").asText());

            ResponseEntity<JsonNode> missingContext = restTemplate.postForEntity(
                    DATASET_API + first.datasetId() + "/network/exports",
                    new HttpEntity<>(emptyRequest, jsonHeaders()),
                    JsonNode.class);
            assertEquals(400, missingContext.getStatusCode().value());
            assertEquals("SN_DATASET_CONTEXT_INVALID",
                    missingContext.getBody().path("code").asText());
            ResponseEntity<JsonNode> mismatchedContext = restTemplate.postForEntity(
                    groupExportUrl(
                            first.datasetId(), second.datasetVersion(), second.sessionId()),
                    new HttpEntity<>(emptyRequest, jsonHeaders()),
                    JsonNode.class);
            assertEquals(400, mismatchedContext.getStatusCode().value());
            assertEquals("SN_DATASET_CONTEXT_INVALID",
                    mismatchedContext.getBody().path("code").asText());

            JsonNode firstAnalysis = runClique(first);
            JsonNode secondAnalysis = runClique(second);
            JsonNode noCliqueAnalysis = runClique(noCliques);
            assertEquals(0, noCliqueAnalysis.path("retainedCliqueCount").asInt());
            assertTrue(noCliqueAnalysis.path("complete").asBoolean());

            Map<String, Object> firstEvidence = Map.of(
                    "type", "clique",
                    "id", "0",
                    "section", "members",
                    "offset", 0,
                    "pageSize", 20);
            Map<String, Object> secondEvidence = Map.of(
                    "type", "clique",
                    "id", "0",
                    "section", "members",
                    "offset", 0,
                    "pageSize", 20);
            for (String format : List.of("json", "csv", "print")) {
                JsonNode firstExport = postCliqueExport(first, format, firstEvidence);
                JsonNode secondExport = postCliqueExport(second, format, secondEvidence);
                String firstContent = firstExport.path("result").asText();
                String secondContent = secondExport.path("result").asText();
                assertEquals(firstContent.getBytes(StandardCharsets.UTF_8).length,
                        firstExport.path("contentLength").asInt());
                assertEquals(secondContent.getBytes(StandardCharsets.UTF_8).length,
                        secondExport.path("contentLength").asInt());
                assertTrue(firstContent.contains(first.sessionId()));
                assertTrue(secondContent.contains(second.sessionId()));
                assertTrue(firstContent.contains("users-e08-first.csv"));
                assertTrue(secondContent.contains("users-e08-second.csv"));
                assertFalse(firstContent.contains("users-e08-second.csv"));
                assertFalse(secondContent.contains("users-e08-first.csv"));

                if ("json".equals(format)) {
                    JsonNode firstFile = new ObjectMapper().readTree(firstContent);
                    JsonNode secondFile = new ObjectMapper().readTree(secondContent);
                    assertEquals(first.sessionId(),
                            firstFile.path("metadata").path("sessionId").asText());
                    assertEquals(second.sessionId(),
                            secondFile.path("metadata").path("sessionId").asText());
                    assertEquals(firstAnalysis.path("dataFingerprint").asText(),
                            firstFile.path("metadata").path("dataFingerprint").asText());
                    assertEquals(secondAnalysis.path("dataFingerprint").asText(),
                            secondFile.path("metadata").path("dataFingerprint").asText());
                    assertEquals(1, firstFile.path("data").path("analysis")
                            .path("cliques").size());
                    assertEquals(1, secondFile.path("data").path("analysis")
                            .path("cliques").size());
                    assertTrue(firstFile.path("data").path("evidence")
                            .path("evidenceAvailable").asBoolean());
                    assertTrue(secondFile.path("data").path("evidence")
                            .path("evidenceAvailable").asBoolean());
                } else if ("print".equals(format)) {
                    assertTrue(firstContent.contains("Clique 极大团分析"));
                    assertTrue(firstContent.contains("本页证据数：3 / 3"));
                } else {
                    assertTrue(firstContent.contains(first.datasetVersion()));
                    assertTrue(secondContent.contains(second.datasetVersion()));
                }
            }

            String emptyReport = postCliqueExport(noCliques, "print", null)
                    .path("result").asText();
            assertTrue(emptyReport.contains("识别到的极大团总数：0"));
            assertTrue(emptyReport.contains("当前页：没有团条目"));

            close(first);
            ResponseEntity<JsonNode> expiredExport = restTemplate.postForEntity(
                    groupExportUrl(first),
                    new HttpEntity<>(emptyRequest, jsonHeaders()),
                    JsonNode.class);
            assertEquals(410, expiredExport.getStatusCode().value());
            assertEquals("SN_DATASET_SESSION_EXPIRED",
                    expiredExport.getBody().path("code").asText());
        } finally {
            close(first);
            close(second);
            close(noCliques);
        }
    }

    @Test
    void uploadedLouvainInsightsAndEvidenceStayBoundToTheirSessionSnapshot() {
        SocialNetworkUploadTaskView.Preview first = uploadReady(List.of(
                new UploadPart(
                        "users-community-first", "users", "users-community-first.csv",
                        "user_id,username\n"
                                + "user-a,first-alpha\nuser-b,first-beta\n"
                                + "user-c,first-gamma\nuser-d,first-delta\n"
                                + "user-e,first-epsilon\nuser-f,first-zeta\n"),
                new UploadPart(
                        "relationships-community-first", "relationships",
                        "relationships-community-first.csv",
                        "source_user_id,target_user_id\n"
                                + "user-a,user-b\nuser-a,user-c\nuser-b,user-c\n"
                                + "user-d,user-e\nuser-d,user-f\nuser-e,user-f\n"
                                + "user-c,user-d\n")));
        SocialNetworkUploadTaskView.Preview second = uploadReady(List.of(
                new UploadPart(
                        "users-community-second", "users", "users-community-second.csv",
                        "user_id,username\nuser-a,second-alpha\nuser-b,second-beta\n"),
                new UploadPart(
                        "relationships-community-second", "relationships",
                        "relationships-community-second.csv",
                        "source_user_id,target_user_id\nuser-a,user-b\n")));
        try {
            JsonNode firstStatus = runLouvain(first);
            JsonNode firstInsights = getData(first, "network/louvain/insights");
            assertTrue(firstInsights.path("available").asBoolean());
            assertEquals("first-alpha",
                    firstInsights.path("names").path("user-a").asText());
            assertEquals("first-zeta",
                    firstInsights.path("names").path("user-f").asText());
            JsonNode crossCommunityPair =
                    firstInsights.path("communityPairs").path(0);
            assertTrue(crossCommunityPair.isObject(),
                    "two dense groups should retain their cross-community relationship");

            String communityId = crossCommunityPair.path("first").asText();
            JsonNode memberEvidence = getData(first,
                    "network/louvain/communities/" + communityId + "/evidence"
                            + "?section=members&pageSize=20");
            assertTrue(memberEvidence.path("evidenceAvailable").asBoolean());
            assertTrue(memberEvidence.path("totalCount").asInt() >= 3);
            assertEquals("file-01-users-community-first.csv",
                    memberEvidence.path("members").path(0)
                            .path("profileSources").path(0)
                            .path("sourceFileName").asText());

            String otherCommunityId = crossCommunityPair.path("second").asText();
            JsonNode crossEvidence = getData(first,
                    "network/louvain/communities/" + communityId + "/between/"
                            + otherCommunityId + "/evidence?pageSize=20");
            assertTrue(crossEvidence.path("evidenceAvailable").asBoolean());
            assertEquals(1, crossEvidence.path("totalCount").asInt());
            JsonNode bridgeSource = crossEvidence.path("ties").path(0)
                    .path("supportingRelationships").path(0);
            assertEquals("file-02-relationships-community-first.csv",
                    bridgeSource.path("sourceFileName").asText());
            assertEquals(8, bridgeSource.path("sourceRowNumber").asInt());
            assertEquals("user-c", bridgeSource.path("sourceId").asText());
            assertEquals("user-d", bridgeSource.path("targetId").asText());

            runLouvain(second);
            JsonNode secondInsights = getData(second, "network/louvain/insights");
            assertTrue(secondInsights.path("available").asBoolean());
            assertEquals("second-alpha",
                    secondInsights.path("names").path("user-a").asText());
            assertEquals("second-beta",
                    secondInsights.path("names").path("user-b").asText());
            assertTrue(secondInsights.path("names").path("user-c").isMissingNode());

            String secondCommunityId = runLouvain(second)
                    .path("communities").path(0).path("id").asText();
            JsonNode secondMembers = getData(second,
                    "network/louvain/communities/" + secondCommunityId
                            + "/evidence?section=members&pageSize=20");
            assertEquals(Set.of("user-a", "user-b"),
                    toStringSet(secondMembers.path("members"), "nodeId"));

            JsonNode firstAgain = getData(first, "network/louvain/insights");
            assertEquals("first-alpha",
                    firstAgain.path("names").path("user-a").asText());
            assertEquals("first-gamma",
                    firstAgain.path("names").path("user-c").asText());
        } finally {
            close(first);
            close(second);
        }
        assertSessionExpired(first, "network/louvain/insights");
        assertSessionExpired(first, "network/louvain/communities/C001/evidence");
        assertSessionExpired(second, "network/louvain/communities/C001/between/C002/evidence");
    }

    @Test
    void uploadedGraphFiltersAndBridgeCandidatesStayBoundToTheirLouvainSession() {
        SocialNetworkUploadTaskView.Preview first = uploadReady(List.of(
                new UploadPart(
                        "users-graph-first", "users", "users-graph-first.csv",
                        "user_id,username\n"
                                + "user-a,first-alpha\nuser-b,first-beta\n"
                                + "user-c,first-gamma\nuser-d,first-delta\n"
                                + "user-e,first-epsilon\nuser-f,first-zeta\n"),
                new UploadPart(
                        "relationships-graph-first", "relationships",
                        "relationships-graph-first.csv",
                        "source_user_id,target_user_id\n"
                                + "user-a,user-b\nuser-a,user-c\nuser-b,user-c\n"
                                + "user-d,user-e\nuser-d,user-f\nuser-e,user-f\n"
                                + "user-c,user-d\n")));
        SocialNetworkUploadTaskView.Preview second = uploadReady(List.of(
                new UploadPart(
                        "users-graph-second", "users", "users-graph-second.csv",
                        "user_id,username\nuser-a,second-alpha\nuser-b,second-beta\n"),
                new UploadPart(
                        "relationships-graph-second", "relationships",
                        "relationships-graph-second.csv",
                        "source_user_id,target_user_id\nuser-a,user-b\n")));
        try {
            JsonNode firstLouvain = runLouvain(first);
            JsonNode firstGraph = getData(first, "network/graph");
            assertTrue(firstGraph.path("communityAssignmentsComplete").asBoolean());
            assertEquals(firstLouvain.path("dataFingerprint").asText(),
                    firstGraph.path("communityDataFingerprint").asText());
            assertTrue(firstGraph.path("globalCommunities").size() >= 2);

            String firstCommunity = firstGraph.path("communityByNodeId")
                    .path("user-c").asText();
            assertFalse(firstCommunity.isBlank());
            JsonNode filtered = getData(first,
                    "network/graph?communityFilter=community&communityId="
                            + encode(firstCommunity));
            assertEquals(firstCommunity,
                    filtered.path("communityFilterCommunityId").asText());
            Set<String> filteredIds = toStringSet(filtered.path("nodes"), "id");
            assertTrue(filteredIds.contains("user-c"));
            filtered.path("nodes").forEach(node ->
                    assertEquals(firstCommunity, node.path("communityId").asText()));
            filtered.path("edges").forEach(edge -> {
                assertTrue(filteredIds.contains(edge.path("source").asText()));
                assertTrue(filteredIds.contains(edge.path("target").asText()));
            });

            JsonNode firstCandidates = getData(first, "network/bridge-candidates");
            assertEquals("available", firstCandidates.path("state").asText());
            assertTrue(firstCandidates.path("totalCount").asInt() > 0);
            assertEquals(first.datasetId(),
                    firstCandidates.path("analysisIdentity").path("datasetId").asText());
            assertEquals(first.datasetVersion(),
                    firstCandidates.path("analysisIdentity").path("datasetVersion").asText());
            assertTrue(firstCandidates.path("items").findValuesAsText("username")
                    .stream().allMatch(name -> name.startsWith("first-")));

            JsonNode secondLouvain = runLouvain(second);
            JsonNode secondGraph = getData(second, "network/graph");
            JsonNode secondCandidates = getData(second, "network/bridge-candidates");
            assertEquals(secondLouvain.path("dataFingerprint").asText(),
                    secondGraph.path("communityDataFingerprint").asText());
            assertEquals(1, secondGraph.path("globalCommunities").size());
            assertEquals("empty", secondCandidates.path("state").asText());
            assertEquals(0, secondCandidates.path("totalCount").asInt());

            JsonNode firstAgain = getData(first, "network/bridge-candidates");
            assertEquals(firstCandidates.path("analysisIdentity").path("analysisResultId"),
                    firstAgain.path("analysisIdentity").path("analysisResultId"));
            assertTrue(firstAgain.path("items").findValuesAsText("username")
                    .stream().allMatch(name -> name.startsWith("first-")));
        } finally {
            close(first);
            close(second);
        }
        assertSessionExpired(first, "network/graph");
        assertSessionExpired(first, "network/bridge-candidates");
        assertSessionExpired(second, "network/bridge-candidates");
    }

    @Test
    void uploadedLouvainAndBridgeExportsStayBoundToTheirSessionsAndFormats() throws Exception {
        SocialNetworkUploadTaskView.Preview first = uploadReady(List.of(
                new UploadPart(
                        "users-e06-first", "users", "users-e06-first.csv",
                        "user_id,username\n"
                                + "user-a,first-alpha\nuser-b,first-beta\n"
                                + "user-c,first-gamma\nuser-d,first-delta\n"
                                + "user-e,first-epsilon\nuser-f,first-zeta\n"),
                new UploadPart(
                        "relationships-e06-first", "relationships",
                        "relationships-e06-first.csv",
                        "source_user_id,target_user_id\n"
                                + "user-a,user-b\nuser-a,user-c\nuser-b,user-c\n"
                                + "user-d,user-e\nuser-d,user-f\nuser-e,user-f\n"
                                + "user-c,user-d\n")));
        SocialNetworkUploadTaskView.Preview second = uploadReady(List.of(
                new UploadPart(
                        "users-e06-second", "users", "users-e06-second.csv",
                        "user_id,username\n"
                                + "user-a,second-alpha\nuser-b,second-beta\n"
                                + "user-c,second-gamma\nuser-d,second-delta\n"
                                + "user-e,second-epsilon\nuser-f,second-zeta\n"),
                new UploadPart(
                        "relationships-e06-second", "relationships",
                        "relationships-e06-second.csv",
                        "source_user_id,target_user_id\n"
                                + "user-a,user-b\nuser-a,user-c\nuser-b,user-c\n"
                                + "user-d,user-e\nuser-d,user-f\nuser-e,user-f\n"
                                + "user-c,user-d\n")));
        try {
            JsonNode firstLouvain = runLouvain(first);
            JsonNode secondLouvain = runLouvain(second);
            String firstCommunity = firstLouvain.path("communities").path(0)
                    .path("id").asText();
            String secondCommunity = secondLouvain.path("communities").path(0)
                    .path("id").asText();
            Map<String, Object> firstEvidence = Map.of(
                    "type", "louvain",
                    "id", firstCommunity,
                    "section", "members",
                    "offset", 0,
                    "pageSize", 20);
            Map<String, Object> secondEvidence = Map.of(
                    "type", "louvain",
                    "id", secondCommunity,
                    "section", "members",
                    "offset", 0,
                    "pageSize", 20);

            JsonNode firstGroupJson = postGroupExport(first, "json", firstEvidence)
                    .path("result");
            JsonNode secondGroupJson = postGroupExport(second, "json", secondEvidence)
                    .path("result");
            JsonNode firstGroup = new ObjectMapper().readTree(firstGroupJson.asText());
            JsonNode secondGroup = new ObjectMapper().readTree(secondGroupJson.asText());
            assertEquals(first.sessionId(),
                    firstGroup.path("metadata").path("sessionId").asText());
            assertEquals(second.sessionId(),
                    secondGroup.path("metadata").path("sessionId").asText());
            assertEquals(first.datasetId(),
                    firstGroup.path("metadata").path("datasetId").asText());
            assertEquals(second.datasetId(),
                    secondGroup.path("metadata").path("datasetId").asText());
            List<String> firstSources = firstGroup.path("data").path("evidence")
                    .path("members").findValuesAsText("sourceFileName");
            List<String> secondSources = secondGroup.path("data").path("evidence")
                    .path("members").findValuesAsText("sourceFileName");
            assertFalse(firstSources.isEmpty());
            assertTrue(firstSources.stream()
                    .allMatch(name -> name.equals("file-01-users-e06-first.csv")));
            assertFalse(secondSources.isEmpty());
            assertTrue(secondSources.stream()
                    .allMatch(name -> name.equals("file-01-users-e06-second.csv")));

            String groupCsv = postGroupExport(first, "csv", null)
                    .path("result").asText();
            String groupPrint = postGroupExport(first, "print", null)
                    .path("result").asText();
            assertTrue(groupCsv.contains(first.sessionId()));
            assertTrue(groupCsv.contains(firstCommunity));
            assertTrue(groupPrint.contains("Louvain 社群分析"));
            assertTrue(groupPrint.contains(first.datasetId()));

            JsonNode firstCandidates = getData(first, "network/bridge-candidates");
            JsonNode secondCandidates = getData(second, "network/bridge-candidates");
            assertTrue(firstCandidates.path("totalCount").asInt() > 0);
            assertTrue(secondCandidates.path("totalCount").asInt() > 0);
            Map<String, Object> firstCandidateQuery = Map.of(
                    "analysisResultId",
                    firstCandidates.path("analysisIdentity")
                            .path("analysisResultId").asText(),
                    "offset", 0,
                    "pageSize", 20);
            JsonNode firstBridge = postBridgeExport(first, "json", firstCandidateQuery)
                    .path("result");
            assertEquals(first.datasetId(),
                    firstBridge.path("analysisIdentity").path("datasetId").asText());
            assertTrue(firstBridge.path("candidates").findValuesAsText("username")
                    .stream().allMatch(name -> name.startsWith("first-")));

            String bridgeCsv = postBridgeExport(first, "csv", firstCandidateQuery)
                    .path("result").asText();
            String bridgePrint = postBridgeExport(first, "print", firstCandidateQuery)
                    .path("result").asText();
            assertTrue(bridgeCsv.contains("first-gamma") || bridgeCsv.contains("first-delta"));
            assertTrue(bridgePrint.contains("跨社群连接候选核对报告"));
            assertTrue(bridgePrint.contains(first.datasetId()));

            close(first);
            String expiredGroupUrl = groupExportUrl(first);
            ResponseEntity<JsonNode> expiredGroup = restTemplate.postForEntity(
                    expiredGroupUrl,
                    new HttpEntity<>(Map.of(
                            "analysisType", "louvain",
                            "format", "json",
                            "offset", 0,
                            "pageSize", 20), jsonHeaders()),
                    JsonNode.class);
            assertEquals(410, expiredGroup.getStatusCode().value());
            assertEquals("SN_DATASET_SESSION_EXPIRED",
                    expiredGroup.getBody().path("code").asText());
            ResponseEntity<JsonNode> expiredBridge = restTemplate.postForEntity(
                    bridgeExportUrl(first),
                    new HttpEntity<>(Map.of(
                            "exportType", "bridge-candidates",
                            "format", "json",
                            "query", firstCandidateQuery,
                            "scope", Map.of()), jsonHeaders()),
                    JsonNode.class);
            assertEquals(410, expiredBridge.getStatusCode().value());
            assertEquals("SN_DATASET_SESSION_EXPIRED",
                    expiredBridge.getBody().path("code").asText());
        } finally {
            close(first);
            close(second);
        }
    }

    @Test
    void uploadedJsonlAnalysesIsolateSameNamedFilesAndPreserveBuiltinResults() {
        BuiltinGroupSnapshot builtinBefore = builtinGroupSnapshot();
        SocialNetworkUploadTaskView.Preview first = uploadReady(List.of(
                new UploadPart(
                        "jsonl-users-a-1", "users", "users.jsonl",
                        "{\"user_id\":\"user-a\",\"username\":\"jsonl-first-alpha\"}\n"),
                new UploadPart(
                        "jsonl-users-a-2", "users", "users.jsonl",
                        "{\"user_id\":\"user-b\",\"username\":\"jsonl-first-beta\"}\n"
                                + "{\"user_id\":\"user-c\",\"username\":\"jsonl-first-gamma\"}\n"),
                new UploadPart(
                        "jsonl-relations-a", "relationships", "relationships.jsonl",
                        "{\"source_user_id\":\"user-a\",\"target_user_id\":\"user-b\"}\n"
                                + "{\"source_user_id\":\"user-b\",\"target_user_id\":\"user-a\"}\n"
                                + "{\"source_user_id\":\"user-a\",\"target_user_id\":\"user-c\"}\n"
                                + "{\"source_user_id\":\"user-c\",\"target_user_id\":\"user-a\"}\n"
                                + "{\"source_user_id\":\"user-b\",\"target_user_id\":\"user-c\"}\n"
                                + "{\"source_user_id\":\"user-c\",\"target_user_id\":\"user-b\"}\n")));
        SocialNetworkUploadTaskView.Preview second = uploadReady(List.of(
                new UploadPart(
                        "jsonl-users-b-1", "users", "users.jsonl",
                        "{\"user_id\":\"user-a\",\"username\":\"jsonl-second-alpha\"}\n"),
                new UploadPart(
                        "jsonl-users-b-2", "users", "users.jsonl",
                        "{\"user_id\":\"user-b\",\"username\":\"jsonl-second-beta\"}\n"
                                + "{\"user_id\":\"user-c\",\"username\":\"jsonl-second-gamma\"}\n"),
                new UploadPart(
                        "jsonl-relations-b", "relationships", "relationships.jsonl",
                        "{\"source_user_id\":\"user-a\",\"target_user_id\":\"user-b\"}\n"
                                + "{\"source_user_id\":\"user-b\",\"target_user_id\":\"user-a\"}\n"
                                + "{\"source_user_id\":\"user-a\",\"target_user_id\":\"user-c\"}\n"
                                + "{\"source_user_id\":\"user-c\",\"target_user_id\":\"user-a\"}\n"
                                + "{\"source_user_id\":\"user-b\",\"target_user_id\":\"user-c\"}\n"
                                + "{\"source_user_id\":\"user-c\",\"target_user_id\":\"user-b\"}\n")));
        try {
            assertNotEquals(first.datasetId(), second.datasetId());

            JsonNode firstLouvain = runLouvain(first);
            JsonNode secondLouvain = runLouvain(second);
            assertTrue(firstLouvain.path("complete").asBoolean());
            assertTrue(secondLouvain.path("complete").asBoolean());
            assertNotEquals(firstLouvain.path("dataFingerprint").asText(),
                    secondLouvain.path("dataFingerprint").asText());
            assertEquals("jsonl-first-alpha",
                    getData(first, "network/louvain/insights")
                            .path("names").path("user-a").asText());
            assertEquals("jsonl-second-alpha",
                    getData(second, "network/louvain/insights")
                            .path("names").path("user-a").asText());

            JsonNode firstClique = runClique(first);
            JsonNode secondClique = runClique(second);
            assertTrue(firstClique.path("complete").asBoolean());
            assertTrue(secondClique.path("complete").asBoolean());
            assertEquals(1, firstClique.path("retainedCliqueCount").asInt());
            assertEquals(1, secondClique.path("retainedCliqueCount").asInt());
            assertNotEquals(firstClique.path("dataFingerprint").asText(),
                    secondClique.path("dataFingerprint").asText());
            assertEquals("jsonl-first-alpha",
                    getData(first, "network/cliques/insights")
                            .path("names").path("user-a").asText());
            assertEquals("jsonl-second-alpha",
                    getData(second, "network/cliques/insights")
                            .path("names").path("user-a").asText());

            JsonNode firstEvidence = getData(first,
                    "network/cliques/0/evidence?section=members&pageSize=20");
            JsonNode secondEvidence = getData(second,
                    "network/cliques/0/evidence?section=members&pageSize=20");
            assertEquals(Set.of("user-a", "user-b", "user-c"),
                    toStringSet(firstEvidence.path("members"), "nodeId"));
            assertEquals(Set.of("user-a", "user-b", "user-c"),
                    toStringSet(secondEvidence.path("members"), "nodeId"));
            assertTrue(firstEvidence.findValuesAsText("sourceFileName").stream()
                    .allMatch(name -> name.contains("users.jsonl")));
            assertTrue(secondEvidence.findValuesAsText("sourceFileName").stream()
                    .allMatch(name -> name.contains("users.jsonl")));
            assertTrue(resultStore.loadClique(firstClique.path("cacheKey").asText()).isEmpty());
            assertTrue(resultStore.loadClique(secondClique.path("cacheKey").asText()).isEmpty());
        } finally {
            close(first);
            close(second);
        }

        assertSessionExpired(first, "network/louvain");
        assertSessionExpired(first, "network/cliques");
        assertSessionExpired(second, "network/louvain");
        assertSessionExpired(second, "network/cliques");
        assertBuiltinGroupSnapshot(builtinBefore);
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

    private JsonNode runLouvain(SocialNetworkUploadTaskView.Preview preview) {
        String path = DATASET_API + preview.datasetId() + "/network/louvain"
                + "?datasetVersion=" + encode(preview.datasetVersion())
                + "&sessionId=" + encode(preview.sessionId());
        ResponseEntity<JsonNode> started =
                restTemplate.postForEntity(path, null, JsonNode.class);
        assertEquals(202, started.getStatusCode().value());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            ResponseEntity<JsonNode> status = restTemplate.getForEntity(path, JsonNode.class);
            assertEquals(200, status.getStatusCode().value());
            assertTrue(status.getBody().path("data").path("complete").asBoolean());
        });
        return restTemplate.getForEntity(path, JsonNode.class).getBody().path("data");
    }

    private JsonNode runClique(SocialNetworkUploadTaskView.Preview preview) {
        String path = cliqueUrl(preview);
        ResponseEntity<JsonNode> started =
                restTemplate.postForEntity(path, null, JsonNode.class);
        assertEquals(202, started.getStatusCode().value());
        assertEquals(preview.datasetId(),
                started.getBody().path("data").path("datasetId").asText());
        assertEquals(preview.datasetVersion(),
                started.getBody().path("data").path("datasetVersion").asText());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            ResponseEntity<JsonNode> status = restTemplate.getForEntity(path, JsonNode.class);
            assertEquals(200, status.getStatusCode().value());
            assertEquals("COMPLETED",
                    status.getBody().path("data").path("state").asText());
        });
        return restTemplate.getForEntity(path, JsonNode.class).getBody().path("data");
    }

    private String cliqueUrl(SocialNetworkUploadTaskView.Preview preview) {
        return cliqueUrl(
                preview.datasetId(), preview.datasetVersion(), preview.sessionId());
    }

    private String cliqueUrl(String datasetId, String datasetVersion, String sessionId) {
        return DATASET_API + datasetId + "/network/cliques"
                + "?datasetVersion=" + encode(datasetVersion)
                + "&sessionId=" + encode(sessionId);
    }

    private JsonNode postGroupExport(
            SocialNetworkUploadTaskView.Preview preview,
            String format,
            Map<String, Object> evidence) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("analysisType", "louvain");
        request.put("format", format);
        request.put("offset", 0);
        request.put("pageSize", 20);
        if (evidence != null) {
            request.put("evidence", evidence);
        }
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                groupExportUrl(preview),
                new HttpEntity<>(request, jsonHeaders()),
                JsonNode.class);
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        return response.getBody().path("data");
    }

    private JsonNode postCliqueExport(
            SocialNetworkUploadTaskView.Preview preview,
            String format,
            Map<String, Object> evidence) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                groupExportUrl(preview),
                new HttpEntity<>(cliqueExportRequest(format, evidence), jsonHeaders()),
                JsonNode.class);
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        return response.getBody().path("data");
    }

    private static Map<String, Object> cliqueExportRequest(
            String format, Map<String, Object> evidence) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("analysisType", "clique");
        request.put("format", format);
        request.put("offset", 0);
        request.put("pageSize", 20);
        if (evidence != null) {
            request.put("evidence", evidence);
        }
        return request;
    }

    private JsonNode postBridgeExport(
            SocialNetworkUploadTaskView.Preview preview,
            String format,
            Map<String, Object> query) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                bridgeExportUrl(preview),
                new HttpEntity<>(Map.of(
                        "exportType", "bridge-candidates",
                        "format", format,
                        "query", query,
                        "scope", Map.of()), jsonHeaders()),
                JsonNode.class);
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        return response.getBody().path("data");
    }

    private static String groupExportUrl(
            SocialNetworkUploadTaskView.Preview preview) {
        return groupExportUrl(
                preview.datasetId(), preview.datasetVersion(), preview.sessionId());
    }

    private static String groupExportUrl(
            String datasetId, String datasetVersion, String sessionId) {
        return DATASET_API + datasetId + "/network/exports"
                + "?datasetVersion=" + encode(datasetVersion)
                + "&sessionId=" + encode(sessionId);
    }

    private static String bridgeExportUrl(
            SocialNetworkUploadTaskView.Preview preview) {
        return DATASET_API + preview.datasetId() + "/exports"
                + "?datasetVersion=" + encode(preview.datasetVersion())
                + "&sessionId=" + encode(preview.sessionId());
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static Set<String> toStringSet(JsonNode array, String field) {
        java.util.HashSet<String> values = new java.util.HashSet<>();
        array.forEach(item -> values.add(item.path(field).asText()));
        return Set.copyOf(values);
    }

    private BuiltinSnapshot builtinSnapshot() {
        JsonNode network = getBuiltinData("network/summary");
        JsonNode interactions = getBuiltinData("interactions/summary");
        JsonNode text = getBuiltinData("text/summary");
        return new BuiltinSnapshot(network, interactions, text);
    }

    private BuiltinGroupSnapshot builtinGroupSnapshot() {
        JsonNode louvain = getBuiltinData("network/louvain");
        JsonNode clique = getBuiltinData("network/cliques?offset=0&pageSize=1");
        return new BuiltinGroupSnapshot(
                louvain.path("state").asText(),
                louvain.path("complete").asBoolean(),
                louvain.path("dataFingerprint").asText(),
                louvain.path("communityByNodeId").deepCopy(),
                louvain.path("communities").deepCopy(),
                clique.path("state").asText(),
                clique.path("complete").asBoolean(),
                clique.path("dataFingerprint").asText(),
                clique.path("retainedCliqueCount").asInt(),
                clique.path("cliques").deepCopy(),
                clique.path("cacheKey").asText());
    }

    private void assertBuiltinGroupSnapshot(BuiltinGroupSnapshot expected) {
        assertEquals(expected, builtinGroupSnapshot());
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
        String separator = resource.contains("?") ? "&" : "?";
        String path = DATASET_API + preview.datasetId() + "/" + resource
                + separator + "datasetVersion=" + encode(preview.datasetVersion())
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

    private record BuiltinGroupSnapshot(
            String louvainState,
            boolean louvainComplete,
            String louvainFingerprint,
            JsonNode communityByNodeId,
            JsonNode communities,
            String cliqueState,
            boolean cliqueComplete,
            String cliqueFingerprint,
            int retainedCliqueCount,
            JsonNode cliquePage,
            String cliqueCacheKey) {
    }
}
