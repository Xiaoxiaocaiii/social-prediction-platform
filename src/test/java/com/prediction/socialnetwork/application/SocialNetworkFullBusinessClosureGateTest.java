package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SocialNetworkFullBusinessClosureGateTest {

    private static final String DATASET =
            "/api/social-network/datasets/think-tank-social-network";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void closesB01ToB07AndExportWithSharedEvidenceContext() throws Exception {
        JsonNode dataset = fetchGet(DATASET).path("data");
        assertEquals("think-tank-social-network", dataset.path("datasetId").asText());
        assertEquals("v1", dataset.path("version").asText());
        assertEquals("limited", dataset.path("state").asText());
        assertEquals("limited", dataset.path("qualitySummary").path("state").asText());
        assertTrue(dataset.path("capabilitySummary").path("items").isArray());

        JsonNode network = fetchGet(DATASET + "/network/summary").path("data");
        assertEquals(626, network.path("nodeCount").asInt());
        assertEquals(12_963, network.path("edgeCount").asInt());
        assertEquals("available", network.path("state").asText());

        JsonNode local = fetchGet(DATASET + "/network/local"
                + "?centerNodeId=1002259603537702912&depth=1&topK=5"
                + "&includeExternalNodes=true").path("data");
        assertEquals("1002259603537702912", local.path("centerNodeId").asText());
        assertTrue(local.path("nodes").isArray());
        assertTrue(local.path("edges").isArray());

        JsonNode ranking = fetchGet(DATASET + "/rankings?metric=pagerank&limit=3").path("data");
        assertEquals("pagerank", ranking.path("metric").asText());
        assertEquals("network", ranking.path("metricType").asText());
        assertEquals("available", ranking.path("state").asText());

        JsonNode user = fetchGet(DATASET + "/users/1002259603537702912").path("data");
        assertEquals("1002259603537702912", user.path("userId").asText());
        assertEquals("available", user.path("profileState").asText());
        assertEquals("available", user.path("tweetEvidence").path("state").asText());

        JsonNode interactions = fetchGet(DATASET + "/interactions/summary").path("data");
        assertEquals("available", interactions.path("state").asText());
        assertTrue(interactions.path("metricDefinitions").has("like_count"));
        assertTrue(interactions.path("metricDefinitions").has("reply_count"));
        assertTrue(interactions.path("metricDefinitions").has("repost_count"));

        JsonNode keywordData = fetchGet(DATASET + "/text/keywords?limit=1").path("data");
        String query = keywordData.path("items").path(0).path("term").asText();
        assertFalse(query.isBlank());

        JsonNode search = fetchGet(DATASET + "/text/search", "query", query)
                .path("data");
        assertEquals("exact-token", search.path("matchMode").asText());
        assertEquals("available", search.path("state").asText());

        JsonNode topic = fetchGet(DATASET + "/text/topic", "query", query)
                .path("data");
        assertEquals(query, topic.path("query").asText());
        assertEquals("exact-token", topic.path("matchMode").asText());
        assertTrue(topic.path("state").asText().equals("available")
                || topic.path("state").asText().equals("limited"));
        assertTrue(topic.path("network").path("edges").isArray());

        String linkedUserId = topic.path("users").path(0).path("userId").asText();
        if (!linkedUserId.isBlank()) {
            JsonNode linkedUser = fetchGet(DATASET + "/users/" + linkedUserId).path("data");
            assertEquals(linkedUserId, linkedUser.path("userId").asText());
            assertTrue(linkedUser.path("networkPosition").isObject());
        }

        JsonNode export = postJson(DATASET + "/exports", """
                {
                  "exportType": "network",
                  "format": "json",
                  "query": {"includeExternalNodes": true},
                  "scope": {"name": "全量规范化关系网络"}
                }
                """).path("data");
        assertEquals("completed", export.path("status").asText());
        assertEquals("available", export.path("resultState").asText());
        assertEquals("think-tank-social-network",
                export.path("metadata").path("datasetId").asText());
        assertEquals("v1", export.path("metadata").path("datasetVersion").asText());
        assertTrue(export.path("metadata").path("metricVersion").isTextual());
        assertTrue(export.path("result").path("nodes").isArray());
    }

    private JsonNode fetchGet(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode fetchGet(String path, String parameter, String value) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .param(parameter, value)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postJson(String path, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
