package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
import com.prediction.socialnetwork.cache.SocialNetworkAnalysisCacheKeyFactory;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
import com.prediction.socialnetwork.network.LouvainCommunityDetector;
import com.prediction.socialnetwork.network.LouvainQualityCalculator;
import com.prediction.socialnetwork.network.LouvainQualitySummary;
import com.prediction.socialnetwork.network.NetworkCommunity;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LouvainAnalysisControllerTest {

    private static final String DATASET_ID =
            SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID;

    @Test
    void exposesExplicitLouvainStartAndStatusRoutes() throws Exception {
        LouvainAnalysisWorkflowService workflow =
                mock(LouvainAnalysisWorkflowService.class);
        SocialNetworkController controller = new SocialNetworkController(
                new SocialNetworkDatasetRegistry(),
                mock(SocialNetworkWorkspaceService.class),
                mock(CliqueEnumerationWorkflowService.class),
                workflow,
                mock(SocialNetworkEvidenceFactSummaryService.class),
                mock(SocialNetworkBridgeCandidateExportService.class),
                new ObjectMapper());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(workflow.request(DATASET_ID, false)).thenReturn(view("RUNNING", false));
        mockMvc.perform(post("/api/social-network/datasets/"
                        + DATASET_ID + "/network/louvain"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.state").value("RUNNING"))
                .andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.meta.dataFingerprint").value("fixture-fingerprint"));
        verify(workflow).request(DATASET_ID, false);

        when(workflow.request(DATASET_ID, true)).thenReturn(view("RUNNING", false));
        mockMvc.perform(post("/api/social-network/datasets/"
                        + DATASET_ID + "/network/louvain")
                        .param("force", "true"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.state").value("RUNNING"));
        verify(workflow).request(DATASET_ID, true);

        when(workflow.status(DATASET_ID)).thenReturn(view("COMPLETED", true));
        mockMvc.perform(get("/api/social-network/datasets/"
                        + DATASET_ID + "/network/louvain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("COMPLETED"))
                .andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.algorithmParameters.resolution").value(1.0))
                .andExpect(jsonPath("$.data.qualityContractVersion")
                        .value(LouvainQualityCalculator.QUALITY_CONTRACT_VERSION));
        verify(workflow).status(DATASET_ID);

        when(workflow.request("upload-session", "session-version", "session-id", false))
                .thenReturn(view("RUNNING", false));
        mockMvc.perform(post(
                        "/api/social-network/datasets/upload-session/network/louvain")
                        .param("datasetVersion", "session-version")
                        .param("sessionId", "session-id"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.state").value("RUNNING"));
        verify(workflow).request("upload-session", "session-version", "session-id", false);

        when(workflow.status("upload-session", "session-version", "session-id"))
                .thenReturn(view("COMPLETED", true));
        mockMvc.perform(get(
                        "/api/social-network/datasets/upload-session/network/louvain")
                        .param("datasetVersion", "session-version")
                        .param("sessionId", "session-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("COMPLETED"));
        verify(workflow).status("upload-session", "session-version", "session-id");
    }

    @Test
    void graphUsesMatchingLouvainResultAndSeparatesFullFromDisplayedAssignments()
            throws Exception {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkDataset dataset = registry.findDataset(DATASET_ID);
        SocialNetworkWorkspaceService realWorkspace =
                new SocialNetworkWorkspaceService(registry);
        SocialNetworkWorkspaceSnapshot snapshot = realWorkspace.get(DATASET_ID);
        var fingerprint = new SocialNetworkDataFingerprintService().fingerprint(dataset);
        var identity = new SocialNetworkAnalysisCacheKeyFactory().louvain(
                dataset, fingerprint, LouvainAnalysisParameters.DEFAULT);
        SocialNetworkWorkspaceService workspace = mock(SocialNetworkWorkspaceService.class);
        when(workspace.get(DATASET_ID)).thenReturn(snapshot);
        when(workspace.getForAnalysis(DATASET_ID, fingerprint.dataFingerprint()))
                .thenReturn(snapshot);

        List<NetworkNode> nodes = snapshot.network().nodes();
        String unassignedId = nodes.stream()
                .filter(NetworkNode::isolated)
                .reduce((first, last) -> last)
                .map(NetworkNode::id)
                .orElse(nodes.get(nodes.size() - 1).id());
        List<NetworkNode> assignedNodes = nodes.stream()
                .filter(node -> !node.id().equals(unassignedId))
                .toList();
        Map<String, String> assignments = new LinkedHashMap<>();
        int communitySplit = assignedNodes.size() / 2;
        for (int index = 0; index < assignedNodes.size(); index++) {
            assignments.put(
                    assignedNodes.get(index).id(),
                    index < communitySplit ? "C001" : "C002");
        }
        LouvainQualitySummary quality = new LouvainQualityCalculator().calculate(
                nodes,
                snapshot.network().edges(),
                assignments,
                LouvainAnalysisParameters.DEFAULT);
        List<NetworkCommunity> communityResults = List.of("C001", "C002").stream()
                .map(communityId -> {
                    List<NetworkNode> members = assignedNodes.stream()
                            .filter(node -> communityId.equals(assignments.get(node.id()))
                            )
                            .toList();
                    int profileCount = (int) members.stream()
                            .filter(node -> node.nodeType() == NetworkNodeType.PROFILE)
                            .count();
                    int externalCount = members.size() - profileCount;
                    int internalTieCount = (int) snapshot.network().edges().stream()
                            .filter(edge -> communityId.equals(assignments.get(edge.source()))
                                    && communityId.equals(assignments.get(edge.target())))
                            .count();
                    return new NetworkCommunity(
                            communityId,
                            members.size(),
                            profileCount,
                            externalCount,
                            internalTieCount);
                })
                .toList();
        LouvainAnalysisWorkflowView completed = new LouvainAnalysisWorkflowView(
                DATASET_ID,
                dataset.datasetVersion(),
                "COMPLETED",
                true,
                true,
                true,
                "COMPLETED",
                null,
                Instant.now().minusSeconds(1),
                Instant.now(),
                nodes.size(),
                snapshot.network().edges().size(),
                LouvainCommunityDetector.ALGORITHM_VERSION,
                identity.parameters(),
                SocialNetworkAnalysisCacheKeyFactory.ANALYSIS_SCOPE,
                fingerprint.dataFingerprint(),
                identity.inputFiles(),
                identity.inputContractVersion(),
                identity.relationProjectionVersion(),
                identity.projection(),
                LouvainQualityCalculator.QUALITY_CONTRACT_VERSION,
                communityResults,
                assignments,
                1,
                List.of(unassignedId),
                1,
                quality,
                10,
                20,
                30,
                40,
                50,
                identity.cacheKey().value(),
                false);
        LouvainAnalysisWorkflowService workflow =
                mock(LouvainAnalysisWorkflowService.class);
        when(workflow.readOnlySnapshot(DATASET_ID)).thenReturn(
                new LouvainAnalysisWorkflowService.ReadOnlySnapshot(
                        completed, Optional.of(completed)));
        SocialNetworkController controller = new SocialNetworkController(
                registry,
                workspace,
                mock(CliqueEnumerationWorkflowService.class),
                workflow,
                mock(SocialNetworkEvidenceFactSummaryService.class),
                mock(SocialNetworkBridgeCandidateExportService.class),
                new ObjectMapper());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        var response = mockMvc.perform(get(
                        "/api/social-network/datasets/" + DATASET_ID + "/network/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.communityAnalysisStatus").value("completed"))
                .andExpect(jsonPath("$.data.communityAssignmentsComplete").value(true))
                .andExpect(jsonPath("$.data.communityAlgorithm").value("Louvain"))
                .andExpect(jsonPath("$.data.communityAlgorithmVersion")
                        .value(LouvainCommunityDetector.ALGORITHM_VERSION))
                .andExpect(jsonPath("$.data.graphDataFingerprint")
                        .value(fingerprint.dataFingerprint()))
                .andExpect(jsonPath("$.data.communityDataFingerprint")
                        .value(fingerprint.dataFingerprint()))
                .andExpect(jsonPath("$.data.communityAssignmentNodeCount")
                        .value(nodes.size()))
                .andExpect(jsonPath("$.data.globalAssignedNodeCount")
                        .value(assignedNodes.size()))
                .andExpect(jsonPath("$.data.displayedAssignedNodeCount")
                        .value((int) nodes.subList(0, 100).stream()
                                .filter(node -> assignments.containsKey(node.id()))
                                .count()))
                .andReturn();

        var data = new ObjectMapper()
                .readTree(response.getResponse().getContentAsString())
                .path("data");
        assertEquals(assignedNodes.size(), data.path("communityByNodeId").size());
        assertEquals(1, data.path("communityUnassignedNodeIds").size());
        assertEquals(2, data.path("globalCommunityCount").asInt());
        assertEquals(1, data.path("displayedCommunityCount").asInt());
        assertTrue(data.path("nodes").size() < data.path("communityByNodeId").size());

        List<NetworkNode> secondCommunityNodes = assignedNodes.stream()
                .filter(node -> "C002".equals(assignments.get(node.id())))
                .toList();
        long secondCommunityEdges = snapshot.network().edges().stream()
                .filter(edge -> "C002".equals(assignments.get(edge.source()))
                        && "C002".equals(assignments.get(edge.target())))
                .count();
        var selectedCommunityResponse = mockMvc.perform(get(
                        "/api/social-network/datasets/" + DATASET_ID + "/network/graph")
                        .param("maxNodes", "20")
                        .param("maxEdges", "4")
                        .param("communityFilter", "community")
                        .param("communityId", "C002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.communityFilter").value("community"))
                .andExpect(jsonPath("$.data.communityFilterCommunityId").value("C002"))
                .andExpect(jsonPath("$.data.communityFilterApplied").value(true))
                .andExpect(jsonPath("$.data.communityFilterNodeCount")
                        .value(secondCommunityNodes.size()))
                .andExpect(jsonPath("$.data.communityFilterEdgeCount")
                        .value((int) secondCommunityEdges))
                .andExpect(jsonPath("$.data.totalNodeCount").value(secondCommunityNodes.size()))
                .andExpect(jsonPath("$.data.totalEdgeCount").value((int) secondCommunityEdges))
                .andExpect(jsonPath("$.data.returnedNodeCount").value(20))
                .andExpect(jsonPath("$.data.nodes[0].communityId").value("C002"))
                .andExpect(jsonPath("$.data.globalCommunityCount").value(2))
                .andExpect(jsonPath("$.data.truncated").value(true))
                .andReturn();
        var selectedData = new ObjectMapper()
                .readTree(selectedCommunityResponse.getResponse().getContentAsString())
                .path("data");
        Set<String> selectedIds = new java.util.LinkedHashSet<>();
        selectedData.path("nodes").forEach(node -> {
            assertEquals("C002", node.path("communityId").asText());
            selectedIds.add(node.path("id").asText());
        });
        selectedData.path("edges").forEach(edge -> {
            assertTrue(selectedIds.contains(edge.path("source").asText()));
            assertTrue(selectedIds.contains(edge.path("target").asText()));
        });

        mockMvc.perform(get(
                        "/api/social-network/datasets/" + DATASET_ID + "/network/graph")
                        .param("maxNodes", "1000")
                        .param("maxEdges", "15000")
                        .param("communityFilter", "unassigned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.communityFilter").value("unassigned"))
                .andExpect(jsonPath("$.data.communityFilterApplied").value(true))
                .andExpect(jsonPath("$.data.communityFilterNodeCount").value(1))
                .andExpect(jsonPath("$.data.totalNodeCount").value(1))
                .andExpect(jsonPath("$.data.nodes[0].id").value(unassignedId))
                .andExpect(jsonPath("$.data.globalUnassignedNodeCount").value(1));
        verify(workspace, times(3))
                .getForAnalysis(DATASET_ID, fingerprint.dataFingerprint());
    }

    private static LouvainAnalysisWorkflowView view(String state, boolean complete) {
        return new LouvainAnalysisWorkflowView(
                DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                state,
                complete,
                complete,
                complete,
                complete ? "COMPLETED" : state,
                null,
                null,
                null,
                626,
                12_963,
                LouvainCommunityDetector.ALGORITHM_VERSION,
                Map.of(
                        "resolution",
                        LouvainAnalysisParameters.DEFAULT.resolution()),
                "full_normalized_network",
                "fixture-fingerprint",
                List.of(),
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                LouvainAnalysisParameters.DEFAULT.relationProjection().resultName(),
                LouvainQualityCalculator.QUALITY_CONTRACT_VERSION,
                List.of(),
                Map.of(),
                0,
                List.of(),
                complete ? 12 : 0,
                null,
                10,
                20,
                30,
                40,
                50,
                "fixture-cache-key",
                false);
    }
}
