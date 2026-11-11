package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SocialNetworkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SocialNetworkUploadTaskService uploadTasks;

    @TempDir
    private Path tempDir;

    @Test
    void exposesDatasetAndNetworkSummaryWithSocialNetworkEnvelope() throws Exception {
        mockMvc.perform(get("/api/social-network/datasets/think-tank-social-network"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.datasetId").value("think-tank-social-network"))
                .andExpect(jsonPath("$.data.state").value("limited"))
                .andExpect(jsonPath("$.data.qualitySummary.datasetStatus")
                        .value("available_with_warnings"))
                .andExpect(jsonPath("$.data.qualitySummary.state").value("limited"))
                .andExpect(jsonPath("$.data.capabilitySummary.items").isArray());

        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/network/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nodeCount").value(626))
                .andExpect(jsonPath("$.data.edgeCount").value(12963))
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.density").isNumber())
                .andExpect(jsonPath("$.data.weaklyConnectedComponentCount").isNumber())
                .andExpect(jsonPath("$.data.largestWeaklyConnectedComponentNodeCount").isNumber())
                .andExpect(jsonPath("$.data.largestWeaklyConnectedComponentRatio").isNumber())
                .andExpect(jsonPath("$.data.averageClusteringCoefficient").isNumber())
                .andExpect(jsonPath("$.data.reciprocityRate").isNumber())
                .andExpect(jsonPath(
                        "$.data.largestWeaklyConnectedComponentAveragePathLength").isNumber())
                .andExpect(jsonPath("$.data.metricVersion").value("network-metrics-v2.0"))
                .andExpect(jsonPath("$.meta.metricVersion").value("network-metrics-v2.0"))
                .andExpect(jsonPath("$.data.pageRank.converged").value(true))
                .andExpect(jsonPath("$.data.interactionScale.available").value(true))
                .andExpect(jsonPath("$.data.interactionScale.metrics.like_count.median")
                        .isNumber())
                .andExpect(jsonPath("$.data.interactionScale.unassignedSourceSampleCount")
                        .isNumber());
    }

    @Test
    void uploadAnalysisRequestsRequireMatchingContextAndReturnExpiredStatusAfterClose()
            throws Exception {
        Path usersFile = tempDir.resolve("users.csv");
        Files.writeString(usersFile, "user_id,username\ncontext-user,context\n");
        SocialNetworkUploadTaskView queued = uploadTasks.submit(
                List.of(new SocialNetworkUploadFileInput(
                        "users-part", "users.csv", DatasetFileRole.USERS, usersFile)),
                null);
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(10))
                .untilAsserted(() -> org.junit.jupiter.api.Assertions.assertEquals(
                        "ready", uploadTasks.getTask(queued.uploadTaskId()).status()));
        SocialNetworkUploadTaskView ready = uploadTasks.getTask(queued.uploadTaskId());
        String datasetId = ready.preview().datasetId();
        String datasetVersion = ready.preview().datasetVersion();
        String sessionId = ready.preview().sessionId();
        String qualityPath = "/api/social-network/datasets/" + datasetId + "/quality/summary";

        mockMvc.perform(get(qualityPath)
                        .param("sessionId", sessionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SN_DATASET_CONTEXT_MISMATCH"));

        mockMvc.perform(get(qualityPath))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SN_DATASET_CONTEXT_MISMATCH"));

        mockMvc.perform(get(qualityPath)
                        .param("datasetVersion", "wrong-version")
                        .param("sessionId", sessionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SN_DATASET_CONTEXT_MISMATCH"));

        mockMvc.perform(get(qualityPath)
                        .param("datasetVersion", datasetVersion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SN_DATASET_CONTEXT_MISMATCH"));

        mockMvc.perform(get(qualityPath)
                        .param("datasetId", "think-tank-social-network")
                        .param("datasetVersion", datasetVersion)
                        .param("sessionId", sessionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SN_DATASET_CONTEXT_MISMATCH"));

        mockMvc.perform(get(qualityPath)
                        .param("datasetVersion", datasetVersion)
                        .param("sessionId", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.datasetId").value(datasetId))
                .andExpect(jsonPath("$.meta.datasetVersion").value(datasetVersion));

        String evidenceSummaryPath = "/api/social-network/datasets/" + datasetId
                + "/network/evidence/summary";
        mockMvc.perform(get(evidenceSummaryPath)
                        .param("datasetVersion", datasetVersion)
                        .param("sessionId", sessionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SN_EVIDENCE_SUMMARY_BUILTIN_ONLY"));

        MvcResult userDetail = mockMvc.perform(get(
                        "/api/social-network/datasets/" + datasetId + "/users/context-user")
                        .param("datasetVersion", datasetVersion)
                        .param("sessionId", sessionId))
                .andExpect(status().isOk())
                .andReturn();
        String localNetworkLink = objectMapper.readTree(userDetail.getResponse().getContentAsString())
                .path("data")
                .path("links")
                .path("localNetwork")
                .asText();
        assertTrue(localNetworkLink.contains("datasetVersion=" + datasetVersion));
        assertTrue(localNetworkLink.contains("sessionId=" + sessionId));

        mockMvc.perform(get(
                        "/api/social-network/datasets/" + datasetId + "/network/graph")
                        .param("datasetVersion", datasetVersion)
                        .param("sessionId", sessionId)
                        .param("communityFilter", "unassigned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graphDataFingerprint")
                        .isNotEmpty())
                .andExpect(jsonPath("$.data.communityDataFingerprint")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.communityAssignmentsComplete").value(false))
                .andExpect(jsonPath("$.data.communityByNodeId").isEmpty())
                .andExpect(jsonPath("$.data.communityFilterAvailable").value(false))
                .andExpect(jsonPath("$.data.communityFilterApplied").value(false))
                .andExpect(jsonPath("$.data.communityFilter").value("all"));

        uploadTasks.closeSession(datasetId, datasetVersion, sessionId);
        mockMvc.perform(get(qualityPath)
                        .param("datasetVersion", datasetVersion)
                        .param("sessionId", sessionId))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SN_DATASET_SESSION_EXPIRED"));
        mockMvc.perform(get(evidenceSummaryPath)
                        .param("datasetVersion", datasetVersion)
                        .param("sessionId", sessionId))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SN_DATASET_SESSION_EXPIRED"));
    }

    @Test
    void searchesUsersByIdentityFields() throws Exception {
        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/users/search")
                        .param("query", "niwcrso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matchedCount").value(1))
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.items[0].userId").value("1002259603537702912"))
                .andExpect(jsonPath("$.data.items[0].username").value("niwcrso"));
    }

    @Test
    void returnsBoundedDirectionalLocalNetwork() throws Exception {
        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/network/local")
                        .param("centerNodeId", "1002259603537702912")
                        .param("depth", "1")
                        .param("topK", "5")
                        .param("includeExternalNodes", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.centerNodeId")
                        .value("1002259603537702912"))
                .andExpect(jsonPath("$.data.depth").value(1))
                .andExpect(jsonPath("$.data.topK").value(5))
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.edges").isArray())
                .andExpect(jsonPath("$.data.limits.maxNodes").value(100))
                .andExpect(jsonPath("$.data.limits.maxEdges").value(500));
    }

    @Test
    void returnsCompleteGlobalNetworkGraphWithStringIdsAndDirectionalEdges() throws Exception {
        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/network/graph")
                        .param("maxNodes", "1000")
                        .param("maxEdges", "15000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scope")
                        .value("全量规范化关系网络（包含外部节点）"))
                .andExpect(jsonPath("$.data.graphScope").value("full_normalized_network"))
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.totalNodeCount").value(626))
                .andExpect(jsonPath("$.data.totalEdgeCount").value(12963))
                .andExpect(jsonPath("$.data.returnedNodeCount").value(626))
                .andExpect(jsonPath("$.data.returnedEdgeCount").value(12963))
                .andExpect(jsonPath("$.data.nodeCount").value(626))
                .andExpect(jsonPath("$.data.edgeCount").value(12963))
                .andExpect(jsonPath("$.data.communityAnalysisStatus").value("not_requested"))
                .andExpect(jsonPath("$.data.globalCommunities").isArray())
                .andExpect(jsonPath("$.data.globalCommunities").isEmpty())
                .andExpect(jsonPath("$.data.displayedCommunities").isArray())
                .andExpect(jsonPath("$.data.displayedCommunities").isEmpty())
                .andExpect(jsonPath("$.data.globalCommunityCount")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.displayedCommunityCount")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.globalUnassignedNodeCount")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.displayedUnassignedNodeCount")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.communityAssignmentScope")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.communityAlgorithm")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.communityProjection")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.communityCount")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.communityConverged")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.communities").isArray())
                .andExpect(jsonPath("$.data.communities").isEmpty())
                .andExpect(jsonPath("$.data.nodes.length()").value(626))
                .andExpect(jsonPath("$.data.edges.length()").value(12963))
                .andExpect(jsonPath("$.data.nodes[0].id").isString())
                .andExpect(jsonPath("$.data.nodes[0].nodeType").isString())
                .andExpect(jsonPath("$.data.edges[0].source").isString())
                .andExpect(jsonPath("$.data.edges[0].target").isString())
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.truncatedBy").isEmpty())
                .andExpect(jsonPath("$.meta.datasetVersion").value("v1"));

        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/network/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("limited"))
                .andExpect(jsonPath("$.data.totalNodeCount").value(626))
                .andExpect(jsonPath("$.data.totalEdgeCount").value(12963))
                .andExpect(jsonPath("$.data.returnedNodeCount").value(100))
                .andExpect(jsonPath("$.data.nodeCount").value(100))
                .andExpect(jsonPath("$.data.returnedEdgeCount").value(
                        org.hamcrest.Matchers.lessThanOrEqualTo(500)))
                .andExpect(jsonPath("$.data.truncated").value(true))
                .andExpect(jsonPath("$.data.truncatedBy")
                        .value(org.hamcrest.Matchers.hasItem("maxNodes")))
                .andExpect(jsonPath("$.data.communityAnalysisStatus").value("not_requested"))
                .andExpect(jsonPath("$.data.globalCommunityCount")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.globalUnassignedNodeCount")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.displayedCommunityCount")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.displayedUnassignedNodeCount")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.limits.maxNodes").value(100))
                .andExpect(jsonPath("$.data.limits.maxEdges").value(500));

        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/network/graph")
                        .param("maxNodes", "1000")
                        .param("maxEdges", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEdgeCount").value(12963))
                .andExpect(jsonPath("$.data.returnedNodeCount").value(626))
                .andExpect(jsonPath("$.data.returnedEdgeCount").value(1))
                .andExpect(jsonPath("$.data.truncated").value(true))
                .andExpect(jsonPath("$.data.truncatedBy")
                        .value(org.hamcrest.Matchers.hasItem("maxEdges")));

        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/network/graph")
                        .param("maxNodes", "1000")
                        .param("maxEdges", "15000")
                        .param("includeExternalNodes", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graphScope").value("profile_nodes_only"))
                .andExpect(jsonPath("$.data.includeExternalNodes").value(false))
                .andExpect(jsonPath("$.data.totalNodeCount")
                        .value(org.hamcrest.Matchers.lessThanOrEqualTo(626)))
                .andExpect(jsonPath("$.data.totalNodeCount")
                        .value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.totalEdgeCount")
                        .value(org.hamcrest.Matchers.lessThanOrEqualTo(12963)))
                .andExpect(jsonPath("$.data.returnedNodeCount")
                        .value(org.hamcrest.Matchers.lessThanOrEqualTo(626)))
                .andExpect(jsonPath("$.data.nodes[*].nodeType")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.equalTo("profile"))));
    }

    @Test
    void exposesIndependentNetworkAndRawAccountRankings() throws Exception {
        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/rankings")
                        .param("metric", "indegree")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.metric").value("indegree"))
                .andExpect(jsonPath("$.data.metricType").value("network"))
                .andExpect(jsonPath("$.data.metricDefinition.label").value("入度"))
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.metricDefinition.scope")
                        .value("全量规范化关系网络中的唯一有效入边数量"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].rank").value(1));

        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/rankings")
                        .param("metric", "followers_count")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metricType").value("raw_account_field"))
                .andExpect(jsonPath("$.data.label").value("粉丝数（原始账户字段）"))
                .andExpect(jsonPath("$.data.items[0].sampleSize").value(1));
    }

    @Test
    void exposesIndependentCoreAndBetweennessRankings() throws Exception {
        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/"
                                + "network/structure-roles")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.algorithmVersion").value("node-roles-v1.0"))
                .andExpect(jsonPath("$.data.nodeCount").value(626))
                .andExpect(jsonPath("$.data.undirectedTieCount").isNumber())
                .andExpect(jsonPath("$.data.kCore.state").value("available"))
                .andExpect(jsonPath("$.data.kCore.items.length()").value(3))
                .andExpect(jsonPath("$.data.kCore.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.kCore.items[0].value").isNumber())
                .andExpect(jsonPath("$.data.kCore.items[0].nodeType").exists())
                .andExpect(jsonPath("$.data.betweenness.state").value("available"))
                .andExpect(jsonPath("$.data.betweenness.items.length()").value(3))
                .andExpect(jsonPath("$.data.betweenness.items[0].value").isNumber())
                .andExpect(jsonPath("$.data.betweenness.items[0].rawValue").isNumber())
                .andExpect(jsonPath("$.data.crossCommunityEvidence.state").value("unavailable"))
                .andExpect(jsonPath("$.data.crossCommunityEvidence.items.length()").value(0))
                .andExpect(jsonPath("$.data.crossCommunityEvidence.reason")
                        .value("社群结果尚未通过负责人复核，暂不提供跨社群辅助证据。"));
    }

    @Test
    void exposesCompleteKCoreDistributionIndependentOfRankingLimit() throws Exception {
        MvcResult response = mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/"
                                + "network/structure-roles")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kCore.items.length()").value(1))
                .andExpect(jsonPath("$.data.kCoreDistribution.state").value("available"))
                .andExpect(jsonPath("$.data.kCoreDistribution.complete").value(true))
                .andExpect(jsonPath("$.data.kCoreDistribution.truncated").value(false))
                .andReturn();

        JsonNode data = objectMapper.readTree(response.getResponse().getContentAsString())
                .path("data");
        JsonNode distribution = data.path("kCoreDistribution");
        JsonNode shells = distribution.path("shells");
        int sampleSize = distribution.path("sampleSize").asInt();
        int exactCountTotal = 0;
        int cumulativeCount = 0;
        java.util.Set<String> nodeIds = new java.util.HashSet<>();

        org.junit.jupiter.api.Assertions.assertEquals(626, sampleSize);
        org.junit.jupiter.api.Assertions.assertTrue(shells.size() > 0);
        for (int index = shells.size() - 1; index >= 0; index--) {
            JsonNode shell = shells.get(index);
            int exactCount = shell.path("exactCount").asInt();
            JsonNode nodes = shell.path("nodes");

            org.junit.jupiter.api.Assertions.assertEquals(index, shell.path("coreNumber").asInt());
            org.junit.jupiter.api.Assertions.assertEquals(exactCount, nodes.size());
            exactCountTotal += exactCount;
            cumulativeCount += exactCount;
            org.junit.jupiter.api.Assertions.assertEquals(
                    cumulativeCount, shell.path("cumulativeCount").asInt());
            org.junit.jupiter.api.Assertions.assertEquals(
                    exactCount / (double) sampleSize,
                    shell.path("exactRatio").asDouble(),
                    1e-12);
            org.junit.jupiter.api.Assertions.assertEquals(
                    cumulativeCount / (double) sampleSize,
                    shell.path("cumulativeRatio").asDouble(),
                    1e-12);
            nodes.forEach(node -> {
                org.junit.jupiter.api.Assertions.assertTrue(
                        nodeIds.add(node.path("nodeId").asText()));
                org.junit.jupiter.api.Assertions.assertTrue(
                        java.util.Set.of("profile", "external")
                                .contains(node.path("nodeType").asText()));
            });
        }

        org.junit.jupiter.api.Assertions.assertEquals(sampleSize, exactCountTotal);
        org.junit.jupiter.api.Assertions.assertEquals(sampleSize, nodeIds.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                shells.size() - 1, distribution.path("maxCoreNumber").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                shells.get(shells.size() - 1).path("exactCount").asInt(),
                distribution.path("highestCoreCount").asInt());
    }

    @Test
    void exposesFullNodeMetricDistributionsWithAlgorithmStatesAndVersions() throws Exception {
        MvcResult response = mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/"
                                + "network/structure-roles")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(response.getResponse().getContentAsString())
                .path("data");
        JsonNode distributions = data.path("metricDistributions");

        org.junit.jupiter.api.Assertions.assertTrue(
                distributions.path("complete").asBoolean());
        org.junit.jupiter.api.Assertions.assertFalse(
                distributions.path("truncated").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals(
                626, distributions.path("populationSize").asInt());
        for (String key : List.of(
                "indegree", "outdegree", "pageRank", "normalizedBetweenness")) {
            JsonNode metric = distributions.path(key);
            org.junit.jupiter.api.Assertions.assertEquals(
                    "available", metric.path("state").asText());
            org.junit.jupiter.api.Assertions.assertEquals(
                    626, metric.path("sampleSize").asInt());
            org.junit.jupiter.api.Assertions.assertEquals(
                    626, metric.path("populationSize").asInt());
            org.junit.jupiter.api.Assertions.assertEquals(
                    0, metric.path("invalidValueCount").asInt());
            for (String statistic : List.of(
                    "zeroCount", "zeroRatio", "min", "mean",
                    "median", "p90", "p95", "max")) {
                org.junit.jupiter.api.Assertions.assertTrue(
                        metric.path(statistic).isNumber(),
                        key + "." + statistic + " should be numeric");
            }
        }

        org.junit.jupiter.api.Assertions.assertEquals(
                "network-metrics-v2.0",
                distributions.path("indegree").path("algorithmVersion").asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                "pagerank-v1.0",
                distributions.path("pageRank").path("algorithmVersion").asText());
        org.junit.jupiter.api.Assertions.assertTrue(
                distributions.path("pageRank").path("diagnostics")
                        .path("converged").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals(
                "node-roles-v1.0",
                distributions.path("normalizedBetweenness")
                        .path("algorithmVersion").asText());
    }

    @Test
    void exposesCrossMetricPairsAndNodeEvidenceFromTheFullPopulation() throws Exception {
        MvcResult response = mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/"
                                + "network/structure-roles")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode crossMetric = objectMapper
                .readTree(response.getResponse().getContentAsString())
                .path("data")
                .path("crossMetric");

        org.junit.jupiter.api.Assertions.assertEquals(
                626, crossMetric.path("populationSize").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(10, crossMetric.path("spearmanPairs").size());
        org.junit.jupiter.api.Assertions.assertEquals(
                10, crossMetric.path("top20Comparisons").size());
        org.junit.jupiter.api.Assertions.assertEquals(
                "indegree",
                crossMetric.path("metricOrder").get(0).asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                "normalizedBetweenness",
                crossMetric.path("metricOrder").get(4).asText());
        for (JsonNode pair : crossMetric.path("spearmanPairs")) {
            org.junit.jupiter.api.Assertions.assertEquals(
                    626, pair.path("sampleSize").asInt());
            org.junit.jupiter.api.Assertions.assertEquals(
                    "average_rank", pair.path("tieMethod").asText());
            org.junit.jupiter.api.Assertions.assertFalse(pair.has("pValue"));
            if (pair.path("rho").isNumber()) {
                org.junit.jupiter.api.Assertions.assertTrue(
                        Math.abs(pair.path("rho").asDouble()) <= 1.0);
            }
        }
        for (JsonNode comparison : crossMetric.path("top20Comparisons")) {
            org.junit.jupiter.api.Assertions.assertEquals(20, comparison.path("k").asInt());
            org.junit.jupiter.api.Assertions.assertTrue(comparison.has("state"));
            org.junit.jupiter.api.Assertions.assertTrue(comparison.has("reason"));
        }

        JsonNode evidence = crossMetric.path("nodeEvidence");
        org.junit.jupiter.api.Assertions.assertTrue(
                crossMetric.path("nodeEvidenceState").isTextual());
        org.junit.jupiter.api.Assertions.assertTrue(evidence.isArray());
        org.junit.jupiter.api.Assertions.assertTrue(evidence.size() > 0);
        java.util.Set<String> nodeIds = new java.util.HashSet<>();
        evidence.forEach(node -> {
            org.junit.jupiter.api.Assertions.assertTrue(nodeIds.add(node.path("nodeId").asText()));
            org.junit.jupiter.api.Assertions.assertTrue(node.path("metrics").isArray());
            node.path("metrics").forEach(metric -> {
                org.junit.jupiter.api.Assertions.assertTrue(metric.path("value").isNumber());
                org.junit.jupiter.api.Assertions.assertTrue(metric.path("rank").asInt() > 0);
            });
        });
    }

    @Test
    void exposesCompleteNodeEvidenceWithStableRanksAndSnapshotIdentity() throws Exception {
        String datasetId = "think-tank-social-network";
        MvcResult evidenceResponse = mockMvc.perform(get(
                        "/api/social-network/datasets/" + datasetId
                                + "/network/evidence/nodes"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult rolesResponse = mockMvc.perform(get(
                        "/api/social-network/datasets/" + datasetId
                                + "/network/structure-roles")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult graphResponse = mockMvc.perform(get(
                        "/api/social-network/datasets/" + datasetId + "/network/graph")
                        .param("maxNodes", "1000")
                        .param("maxEdges", "1"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode evidenceData = objectMapper
                .readTree(evidenceResponse.getResponse().getContentAsString())
                .path("data");
        JsonNode structureData = objectMapper
                .readTree(rolesResponse.getResponse().getContentAsString())
                .path("data");
        JsonNode graphNodes = objectMapper
                .readTree(graphResponse.getResponse().getContentAsString())
                .path("data")
                .path("nodes");
        JsonNode items = evidenceData.path("items");
        JsonNode coreShells = structureData.path("kCoreDistribution").path("shells");

        org.junit.jupiter.api.Assertions.assertEquals(626, evidenceData.path("sampleSize").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                evidenceData.path("sampleSize").asInt(),
                evidenceData.path("returnedNodeCount").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(items.size(), graphNodes.size());
        org.junit.jupiter.api.Assertions.assertTrue(evidenceData.path("complete").asBoolean());
        org.junit.jupiter.api.Assertions.assertFalse(evidenceData.path("truncated").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals(
                evidenceData.path("analysisSnapshot"),
                structureData.path("analysisSnapshot"));

        List<String> orderedNodeIds = new java.util.ArrayList<>();
        Map<String, JsonNode> graphNodeById = new java.util.LinkedHashMap<>();
        graphNodes.forEach(node -> graphNodeById.put(node.path("id").asText(), node));
        Map<String, Integer> coreByNodeId = new java.util.HashMap<>();
        coreShells.forEach(shell -> shell.path("nodes").forEach(node ->
                coreByNodeId.put(
                        node.path("nodeId").asText(),
                        shell.path("coreNumber").asInt())));
        Map<String, Map<String, Double>> valuesByMetric = new java.util.LinkedHashMap<>();
        for (String metricKey : List.of(
                "indegree", "outdegree", "pageRank", "kCore", "normalizedBetweenness")) {
            valuesByMetric.put(metricKey, new java.util.LinkedHashMap<>());
        }

        boolean sawProfileWithData = false;
        for (JsonNode item : items) {
            String nodeId = item.path("nodeId").asText();
            orderedNodeIds.add(nodeId);
            String nodeType = item.path("nodeType").asText();
            String profileState = item.path("profileState").asText();
            if ("external".equals(nodeType)) {
                org.junit.jupiter.api.Assertions.assertEquals("unavailable", profileState);
            } else if ("available".equals(profileState)) {
                sawProfileWithData = true;
            }

            JsonNode graphNode = graphNodeById.get(nodeId);
            org.junit.jupiter.api.Assertions.assertNotNull(graphNode);
            for (JsonNode metric : item.path("metrics")) {
                String metricKey = metric.path("metricKey").asText();
                if (metric.path("value").isNumber()) {
                    double value = metric.path("value").asDouble();
                    valuesByMetric.get(metricKey).put(nodeId, value);
                    if ("indegree".equals(metricKey)) {
                        org.junit.jupiter.api.Assertions.assertEquals(
                                graphNode.path("inDegree").asDouble(), value, 1e-12);
                    } else if ("outdegree".equals(metricKey)) {
                        org.junit.jupiter.api.Assertions.assertEquals(
                                graphNode.path("outDegree").asDouble(), value, 1e-12);
                    } else if ("pageRank".equals(metricKey)) {
                        org.junit.jupiter.api.Assertions.assertEquals(
                                graphNode.path("pageRank").asDouble(), value, 1e-12);
                    } else if ("kCore".equals(metricKey)) {
                        org.junit.jupiter.api.Assertions.assertEquals(
                                coreByNodeId.get(nodeId).doubleValue(), value, 1e-12);
                    }
                }
            }
        }

        org.junit.jupiter.api.Assertions.assertEquals(
                orderedNodeIds.stream().sorted().toList(), orderedNodeIds);
        org.junit.jupiter.api.Assertions.assertTrue(sawProfileWithData);
        org.junit.jupiter.api.Assertions.assertEquals(
                evidenceData.path("sampleSize").asInt(),
                evidenceData.path("profileAvailableCount").asInt()
                        + evidenceData.path("profileUnavailableCount").asInt());

        for (Map.Entry<String, Map<String, Double>> metric : valuesByMetric.entrySet()) {
            List<Map.Entry<String, Double>> ranked = metric.getValue().entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .toList();
            int currentRank = 0;
            double previousValue = Double.NaN;
            Map<String, Integer> expectedRanks = new java.util.HashMap<>();
            for (int index = 0; index < ranked.size(); index++) {
                Map.Entry<String, Double> item = ranked.get(index);
                if (index == 0 || Double.compare(item.getValue(), previousValue) != 0) {
                    currentRank = index + 1;
                    previousValue = item.getValue();
                }
                expectedRanks.put(item.getKey(), currentRank);
            }
            for (JsonNode node : items) {
                String nodeId = node.path("nodeId").asText();
                for (JsonNode metricEvidence : node.path("metrics")) {
                    if (metric.getKey().equals(metricEvidence.path("metricKey").asText())
                            && expectedRanks.containsKey(nodeId)) {
                        org.junit.jupiter.api.Assertions.assertEquals(
                                expectedRanks.get(nodeId).intValue(),
                                metricEvidence.path("rank").asInt(),
                                metric.getKey() + " rank for " + nodeId);
                    }
                }
            }
        }
    }

    @Test
    void exposesCachedFactSummaryForTheFullSnapshotIndependentOfDisplayLimits()
            throws Exception {
        String datasetId = "think-tank-social-network";
        mockMvc.perform(get(
                        "/api/social-network/datasets/" + datasetId
                                + "/network/structure-roles")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kCore.items.length()").value(1));

        String path = "/api/social-network/datasets/" + datasetId
                + "/network/evidence/summary";
        MvcResult firstResponse = mockMvc.perform(get(path).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.facts.length()").value(28))
                .andExpect(jsonPath("$.data.networkInterpretations.length()").value(27))
                .andExpect(jsonPath("$.data.cannotConclude.length()").value(4))
                .andReturn();
        MvcResult repeatedResponse = mockMvc.perform(get(path).param("limit", "100"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstData = objectMapper
                .readTree(firstResponse.getResponse().getContentAsString())
                .path("data");
        JsonNode repeatedData = objectMapper
                .readTree(repeatedResponse.getResponse().getContentAsString())
                .path("data");
        org.junit.jupiter.api.Assertions.assertEquals(firstData, repeatedData);
        org.junit.jupiter.api.Assertions.assertEquals(
                "think-tank-social-network",
                firstData.path("analysisSnapshot").path("datasetId").asText());
        JsonNode coreFact = firstData.path("facts").findValue("shells");
        org.junit.jupiter.api.Assertions.assertTrue(coreFact.isArray());
        int coreNodeCount = 0;
        for (JsonNode shell : coreFact) {
            coreNodeCount += shell.path("nodeIds").size();
        }
        org.junit.jupiter.api.Assertions.assertEquals(626, coreNodeCount);
        for (JsonNode fact : firstData.path("facts")) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    fact.path("evidenceReferences").isArray()
                            && fact.path("evidenceReferences").size() > 0);
        }
    }

    @Test
    void exposesUserEvidenceDetailWithSeparateTweetAndInteractionScopes() throws Exception {
        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/users/"
                                + "1002259603537702912"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("1002259603537702912"))
                .andExpect(jsonPath("$.data.profileState").value("available"))
                .andExpect(jsonPath("$.data.profile.status").value("已采集"))
                .andExpect(jsonPath("$.data.networkPosition.inDegree").isNumber())
                .andExpect(jsonPath("$.data.rawAccountFields.declaredTweetCount").isNumber())
                .andExpect(jsonPath("$.data.rawAccountFields.state").value("available"))
                .andExpect(jsonPath("$.data.tweetEvidence.sampleCount").isNumber())
                .andExpect(jsonPath("$.data.tweetEvidence.state").value("available"))
                .andExpect(jsonPath("$.data.tweetEvidence.interactions.like_count").exists())
                .andExpect(jsonPath("$.data.textSummary.state").value("unavailable"))
                .andExpect(jsonPath("$.data.neighbors.items").isArray());
    }

    @Test
    void exposesIndependentInteractionSummaryRankingsAndTimeseries() throws Exception {
        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/interactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.like_count.total").isNumber())
                .andExpect(jsonPath("$.data.metrics.like_count.average").isNumber())
                .andExpect(jsonPath("$.data.metrics.like_count.median").isNumber())
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.metricDefinitions.like_count.label")
                        .value("点赞数"))
                .andExpect(jsonPath("$.data.metricDefinitions.reply_count.type")
                        .value("interaction"))
                .andExpect(jsonPath("$.data.scope").value(
                        "去重且纳入分析的推文样本；未归属来源保留在全局统计中"));

        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/interactions/tweets")
                        .param("metric", "reply_count")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metric").value("reply_count"))
                .andExpect(jsonPath("$.data.metricDefinition.label").value("评论数"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.items[0].value").isNumber());

        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/interactions/users")
                        .param("metric", "repost_count")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].userId").isString())
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.items[0].average").isNumber());

        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/interactions/timeseries")
                        .param("metric", "like_count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].period").isString())
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.items[0].sampleCount").isNumber());
    }

    @Test
    void exposesTextCoverageKeywordsTfidfAndExactTokenSearch() throws Exception {
        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/text/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.languageCounts").exists())
                .andExpect(jsonPath("$.data.validTextSampleCount").isNumber())
                .andExpect(jsonPath("$.data.originalTextPreserved").value(true));

        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/text/keywords")
                        .param("language", "en")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].term").isString())
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.items[0].documentFrequency").isNumber());

        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/text/tfidf")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].score").isNumber())
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.formula").isString());
    }

    @Test
    void linksExactTopicMatchesToUsersAndRealFollowingEdges() throws Exception {
        MvcResult keywordResult = mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/text/keywords")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].term").isString())
                .andReturn();
        JsonNode keywordBody = objectMapper.readTree(keywordResult.getResponse().getContentAsString());
        String query = keywordBody.path("data").path("items").get(0).path("term").asText();

        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/text/search")
                        .param("query", query)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchMode").value("exact-token"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.state").value("available"));

        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/text/topic")
                        .param("query", query)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.query").value(query))
                .andExpect(jsonPath("$.data.normalizedQuery").value(query.toLowerCase()))
                .andExpect(jsonPath("$.data.matchMode").value("exact-token"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.state").value("available"))
                .andExpect(jsonPath("$.data.matchedTweetCount").isNumber())
                .andExpect(jsonPath("$.data.relatedUserCount").isNumber())
                .andExpect(jsonPath("$.data.tweets").isArray())
                .andExpect(jsonPath("$.data.tweets[0].tweetId").isString())
                .andExpect(jsonPath("$.data.tweets[0].textOriginal").isString())
                .andExpect(jsonPath("$.data.users").isArray())
                .andExpect(jsonPath("$.data.network.nodeCount").isNumber())
                .andExpect(jsonPath("$.data.network.edgeCount").isNumber())
                .andExpect(jsonPath("$.data.network.edges").isArray())
                .andExpect(jsonPath("$.data.network.externalNodesIncluded").value(false))
                .andExpect(jsonPath("$.data.textProcessingVersion").isString());
    }

    @Test
    void createsSynchronousJsonExportWithTraceabilityMetadata() throws Exception {
        mockMvc.perform(post(
                        "/api/social-network/datasets/think-tank-social-network/exports")
                        .contentType("application/json")
                        .content("""
                                {
                                  "exportType": "network",
                                  "format": "json",
                                  "query": {"includeExternalNodes": true},
                                  "scope": {"name": "全量规范化关系网络"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.statusLabel").value("已完成"))
                .andExpect(jsonPath("$.data.exportId").isString())
                .andExpect(jsonPath("$.data.downloadAvailable").value(false))
                .andExpect(jsonPath("$.data.metadata.exportedAt").isString())
                .andExpect(jsonPath("$.data.format").value("json"))
                .andExpect(jsonPath("$.data.resultState").value("available"))
                .andExpect(jsonPath("$.data.contentType")
                        .value("application/json; charset=UTF-8"))
                .andExpect(jsonPath("$.data.contentLength").isNumber())
                .andExpect(jsonPath("$.data.metadata.datasetId")
                        .value("think-tank-social-network"))
                .andExpect(jsonPath("$.data.exportType").value("network"))
                .andExpect(jsonPath("$.data.metadata.query").isEmpty())
                .andExpect(jsonPath("$.data.metadata.algorithmVersions.networkMetrics")
                        .value("network-metrics-v2.0"))
                .andExpect(jsonPath("$.data.metadata.algorithmVersions.pageRank")
                        .value("pagerank-v1.0"))
                .andExpect(jsonPath("$.data.metadata.algorithmVersions.structuralRoles")
                        .value("node-roles-v1.0"))
                .andExpect(jsonPath("$.data.metadata.algorithmVersions.evidenceSummary")
                        .value("evidence-facts-v1"))
                .andExpect(jsonPath("$.data.metadata.sampleCounts.returnedCount").value(1))
                .andExpect(jsonPath("$.data.metadata.scope.calculationScope")
                        .value("完整规范化网络快照"))
                .andExpect(jsonPath("$.data.result.nodeCount").value(626))
                .andExpect(jsonPath("$.data.result.edgeCount").value(12963))
                .andExpect(jsonPath("$.data.result.metricVersion")
                        .value("network-metrics-v2.0"))
                .andExpect(jsonPath("$.data.result.interactionScale").exists())
                .andExpect(jsonPath(
                        "$.data.result.structureEvidence.kCoreDistribution.sampleSize")
                        .value(626))
                .andExpect(jsonPath(
                        "$.data.result.structureEvidence.kCoreDistribution.complete")
                        .value(true))
                .andExpect(jsonPath(
                        "$.data.result.structureEvidence.metricDistributions.populationSize")
                        .value(626))
                .andExpect(jsonPath("$.data.result.evidenceSummary.analysisSnapshot.datasetId")
                        .value("think-tank-social-network"))
                .andExpect(jsonPath("$.data.result.evidenceSummary.facts").isArray())
                .andExpect(jsonPath("$.data.result.evidenceSummary.facts.length()")
                        .value(28))
                .andExpect(jsonPath("$.data.result.nodes").doesNotExist())
                .andExpect(jsonPath("$.data.result.edges").doesNotExist());
    }

    @Test
    void exportsCurrentImportantNodeMetricAndReportsTopTwentyTruncation() throws Exception {
        mockMvc.perform(post(
                        "/api/social-network/datasets/think-tank-social-network/exports")
                        .contentType("application/json")
                        .content("""
                                {
                                  "exportType": "users",
                                  "format": "json",
                                  "query": {"metric": "pagerank"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.metadata.query.metric").value("pagerank"))
                .andExpect(jsonPath("$.data.result.ranking.metric").value("pagerank"))
                .andExpect(jsonPath("$.data.result.ranking.items.length()").value(20))
                .andExpect(jsonPath("$.data.result.ranking.truncated").value(true))
                .andExpect(jsonPath(
                        "$.data.result.structuralRoles.metricDistributions.populationSize")
                        .value(626))
                .andExpect(jsonPath(
                        "$.data.result.structuralRoles.crossMetric.spearmanPairs.length()")
                        .value(10))
                .andExpect(jsonPath(
                        "$.data.result.structuralRoles.crossMetric.nodeEvidence").isArray())
                .andExpect(jsonPath("$.data.result.structuralRoles.kCore.items").isArray())
                .andExpect(jsonPath("$.data.result.structuralRoles.betweenness.items").isArray())
                .andExpect(jsonPath("$.data.metadata.algorithmVersions.pageRank")
                        .value("pagerank-v1.0"))
                .andExpect(jsonPath("$.data.result.truncated").value(true));
    }

    @Test
    void exportsCurrentInteractionMetricWithBoundedListsAndCounts() throws Exception {
        mockMvc.perform(post(
                        "/api/social-network/datasets/think-tank-social-network/exports")
                        .contentType("application/json")
                        .content("""
                                {
                                  "exportType": "interactions",
                                  "format": "json",
                                  "query": {"metric": "reply_count"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.metadata.query.metric").value("reply_count"))
                .andExpect(jsonPath("$.data.result.metric").value("reply_count"))
                .andExpect(jsonPath("$.data.result.summary.metrics.reply_count").exists())
                .andExpect(jsonPath("$.data.result.tweets.metric").value("reply_count"))
                .andExpect(jsonPath("$.data.result.tweets.items.length()").value(10))
                .andExpect(jsonPath("$.data.result.tweets.totalCount").isNumber())
                .andExpect(jsonPath("$.data.result.tweets.truncated").value(true))
                .andExpect(jsonPath("$.data.result.users.metric").value("reply_count"))
                .andExpect(jsonPath("$.data.result.timeseries.items.length()").isNumber());
    }

    @Test
    void exportsTopicQueryAndVisibleOriginalText() throws Exception {
        MvcResult keywordResult = mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/text/keywords")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andReturn();
        String term = objectMapper.readTree(keywordResult.getResponse().getContentAsString())
                .path("data").path("items").get(0).path("term").asText();
        String request = objectMapper.writeValueAsString(java.util.Map.of(
                "exportType", "topic",
                "format", "json",
                "query", java.util.Map.of("term", term)));

        mockMvc.perform(post(
                        "/api/social-network/datasets/think-tank-social-network/exports")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.metadata.query.term").value(term))
                .andExpect(jsonPath("$.data.result.query").value(term))
                .andExpect(jsonPath("$.data.result.returnedTweetCount").isNumber())
                .andExpect(jsonPath("$.data.result.tweets[0].textOriginal").isString())
                .andExpect(jsonPath("$.data.result.network.returnedEdgeCount").isNumber())
                .andExpect(jsonPath("$.data.result.truncated").isBoolean());
    }

    @Test
    void exportsQualitySummaryAndWarnings() throws Exception {
        mockMvc.perform(post(
                        "/api/social-network/datasets/think-tank-social-network/exports")
                        .contentType("application/json")
                        .content("""
                                {"exportType":"quality","format":"json"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.result.datasetStatus")
                        .value("available_with_warnings"))
                .andExpect(jsonPath("$.data.result.summary").isMap())
                .andExpect(jsonPath("$.data.result.coverage").isMap())
                .andExpect(jsonPath("$.data.result.warnings").isArray())
                .andExpect(jsonPath("$.data.result.warnings.length()").value(1));
    }

    @Test
    void printableModuleExportsReflectTheirTypeAndSelectedQuery() throws Exception {
        String pageRankReport = printExport("users", Map.of("metric", "pagerank"));
        String outdegreeReport = printExport("users", Map.of("metric", "outdegree"));
        assertTrue(pageRankReport.contains("\u7f51\u7edc\u4f4d\u7f6e\u4f30\u7b97"));
        assertTrue(outdegreeReport.contains("\u4e3b\u52a8\u5173\u6ce8\u6570"));
        assertTrue(!pageRankReport.equals(outdegreeReport));

        String repliesReport = printExport(
                "interactions", Map.of("metric", "reply_count"));
        String likesReport = printExport(
                "interactions", Map.of("metric", "like_count"));
        assertTrue(repliesReport.contains("\u8bc4\u8bba\u6570"));
        assertTrue(likesReport.contains("\u70b9\u8d5e\u6570"));
        assertTrue(!repliesReport.equals(likesReport));

        MvcResult keywordResult = mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/text/keywords")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andReturn();
        String term = objectMapper.readTree(
                keywordResult.getResponse().getContentAsString())
                .path("data").path("items").get(0).path("term").asText();
        String topicReport = printExport("topic", Map.of("term", term));
        assertTrue(topicReport.contains(term));
        assertTrue(topicReport.contains(
                "\u4e3b\u9898\u67e5\u8be2\u5206\u6790\u62a5\u544a"));

        String qualityReport = printExport("quality", Map.of());
        assertTrue(qualityReport.contains(
                "\u793e\u4ea4\u7f51\u7edc\u6570\u636e\u8d28\u91cf\u5206\u6790\u62a5\u544a"));
        assertTrue(!topicReport.equals(qualityReport));
    }

    private String printExport(String exportType, Map<String, Object> query)
            throws Exception {
        String request = objectMapper.writeValueAsString(Map.of(
                "exportType", exportType,
                "format", "print",
                "query", query));
        MvcResult response = mockMvc.perform(post(
                        "/api/social-network/datasets/think-tank-social-network/exports")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(
                response.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return body.path("data").path("result").asText();
    }

    @Test
    void rejectsUnsupportedExportFormat() throws Exception {
        mockMvc.perform(post(
                        "/api/social-network/datasets/think-tank-social-network/exports")
                        .contentType("application/json")
                        .content("""
                                {"exportType":"network","format":"xml"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SN_BAD_REQUEST"));
    }

    @Test
    void returnsUnifiedEmptyResultMessage() throws Exception {
        mockMvc.perform(post(
                        "/api/social-network/datasets/think-tank-social-network/exports")
                        .contentType("application/json")
                        .content("""
                                {
                                  "exportType": "topic",
                                  "format": "json",
                                  "query": {"term": "__no_such_term__"}
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SN_EXPORT_EMPTY_RESULT"))
                .andExpect(jsonPath("$.message")
                        .value("当前查询条件和筛选范围没有可导出的真实结果。"));
    }

    @Test
    void createsCsvExportContentForBoundedModuleResults() throws Exception {
        mockMvc.perform(post(
                        "/api/social-network/datasets/think-tank-social-network/exports")
                        .contentType("application/json")
                        .content("""
                                {
                                  "exportType": "network",
                                  "format": "csv"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exportType").value("network"))
                .andExpect(jsonPath("$.data.format").value("csv"))
                .andExpect(jsonPath("$.data.contentType")
                        .value("text/csv; charset=UTF-8"))
                .andExpect(jsonPath("$.data.resultState").value("available"))
                .andExpect(jsonPath("$.data.contentLength").isNumber())
                .andExpect(jsonPath("$.data.result").isString())
                .andExpect(jsonPath("$.data.result").value(
                        org.hamcrest.Matchers.containsString("recordType")))
                .andExpect(jsonPath("$.data.result").value(
                        org.hamcrest.Matchers.containsString("structureEvidence")))
                .andExpect(jsonPath("$.data.result").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("sourceUserId"))))
                .andExpect(jsonPath("$.data.result").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("targetUserId"))));
    }

    @Test
    void rejectsOversizedLegacyFullTweetExportWith413() throws Exception {
        mockMvc.perform(post(
                        "/api/social-network/datasets/think-tank-social-network/exports")
                        .contentType("application/json")
                        .content("""
                                {"exportType":"tweets","format":"csv"}
                                """))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SN_EXPORT_TOO_LARGE"))
                .andExpect(jsonPath("$.message")
                        .value("导出结果超过单次响应上限（2 MiB），请缩小范围后重试。"));
    }

    @Test
    void rejectsModuleExportWhenVisibleOriginalTextExceedsTwoMib() throws Exception {
        Path usersFile = tempDir.resolve("large-export-users.csv");
        Path tweetsFile = tempDir.resolve("large-export-tweets.csv");
        Files.writeString(usersFile, "user_id,username\nlarge-export-user,large\n");
        Files.writeString(tweetsFile,
                "tweet_id,user_id,created_at,text\n"
                        + "large-export-tweet,large-export-user,2026-10-19,\"needle "
                        + " ".repeat(2 * 1024 * 1024) + "\"\n");
        SocialNetworkUploadTaskView queued = uploadTasks.submit(
                List.of(
                        new SocialNetworkUploadFileInput(
                                "large-users", "users.csv", DatasetFileRole.USERS, usersFile),
                        new SocialNetworkUploadFileInput(
                                "large-tweets", "tweets.csv", DatasetFileRole.TWEETS, tweetsFile)),
                null);
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(15))
                .until(() -> List.of("ready", "failed").contains(
                        uploadTasks.getTask(queued.uploadTaskId()).status()));
        SocialNetworkUploadTaskView ready = uploadTasks.getTask(queued.uploadTaskId());
        org.junit.jupiter.api.Assertions.assertEquals(
                "ready", ready.status(), String.valueOf(ready.error()));
        SocialNetworkUploadTaskView.Preview preview = ready.preview();

        mockMvc.perform(post(
                        "/api/social-network/datasets/" + preview.datasetId() + "/exports")
                        .param("datasetVersion", preview.datasetVersion())
                        .param("sessionId", preview.sessionId())
                        .contentType("application/json")
                        .content("""
                                {
                                  "exportType": "topic",
                                  "format": "json",
                                  "query": {"term": "needle"}
                                }
                                """))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SN_EXPORT_TOO_LARGE"));

        uploadTasks.closeSession(
                preview.datasetId(), preview.datasetVersion(), preview.sessionId());
    }

    @Test
    void createsPrintableExportSummaryWithSharedMetadata() throws Exception {
        mockMvc.perform(post(
                        "/api/social-network/datasets/think-tank-social-network/exports")
                        .contentType("application/json")
                        .content("""
                                {
                                  "exportType": "network",
                                  "format": "print",
                                  "query": {"includeExternalNodes": true},
                                  "scope": {"name": "全量规范化关系网络"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.format").value("print"))
                .andExpect(jsonPath("$.data.contentType")
                        .value("text/plain; charset=UTF-8"))
                .andExpect(jsonPath("$.data.contentLength").isNumber())
                .andExpect(jsonPath("$.data.contentLength")
                        .value(org.hamcrest.Matchers.lessThanOrEqualTo(2 * 1024 * 1024)))
                .andExpect(jsonPath("$.data.result").isString())
                .andExpect(jsonPath("$.data.result").value(
                        org.hamcrest.Matchers.containsString("社交网络结构分析报告")))
                .andExpect(jsonPath("$.data.result").value(
                        org.hamcrest.Matchers.containsString(
                                "一、结论摘要")))
                .andExpect(jsonPath("$.data.result").value(
                        org.hamcrest.Matchers.containsString(
                                "当前完整网络包含 626 个账号节点和 12,963 条有效关注关系。")))
                .andExpect(jsonPath("$.data.result").value(
                        org.hamcrest.Matchers.containsString(
                                "网络位置估算已稳定。")))
                .andExpect(jsonPath("$.data.result").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                "data.nodeCount"))))
                .andExpect(jsonPath("$.data.result").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                "evidenceSummary="))));
    }

    @Test
    void returnsEmptyStateForBlankTextQuery() throws Exception {
        mockMvc.perform(get(
                        "/api/social-network/datasets/think-tank-social-network/text/search")
                        .param("query", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.state").value("empty"))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }
}
