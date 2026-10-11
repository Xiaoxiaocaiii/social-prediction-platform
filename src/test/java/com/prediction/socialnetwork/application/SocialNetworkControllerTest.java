package com.prediction.socialnetwork.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                .andExpect(jsonPath("$.data.pageRank.converged").value(true))
                .andExpect(jsonPath("$.data.interactionScale.available").value(true))
                .andExpect(jsonPath("$.data.interactionScale.metrics.like_count.median")
                        .isNumber())
                .andExpect(jsonPath("$.data.interactionScale.unassignedSourceSampleCount")
                        .isNumber());
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
                .andExpect(jsonPath("$.data.metadata.query.includeExternalNodes")
                        .value(true))
                .andExpect(jsonPath("$.data.result.nodes").isArray());
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
    void createsCsvExportContentWithChineseAndStringFields() throws Exception {
        mockMvc.perform(post(
                        "/api/social-network/datasets/think-tank-social-network/exports")
                        .contentType("application/json")
                        .content("""
                                {
                                  "exportType": "tweets",
                                  "format": "csv",
                                  "scope": {"name": "去重且纳入分析的推文样本"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.format").value("csv"))
                .andExpect(jsonPath("$.data.contentType")
                        .value("text/csv; charset=UTF-8"))
                .andExpect(jsonPath("$.data.resultState").value("available"))
                .andExpect(jsonPath("$.data.contentLength").isNumber())
                .andExpect(jsonPath("$.data.result").isString())
                .andExpect(jsonPath("$.data.result").value(
                        org.hamcrest.Matchers.containsString("tweetId")));
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
                .andExpect(jsonPath("$.data.result").isString())
                .andExpect(jsonPath("$.data.result").value(
                        org.hamcrest.Matchers.containsString("数据集: think-tank-social-network")));
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
