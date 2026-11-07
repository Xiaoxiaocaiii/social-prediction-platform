package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.network.CliqueEnumerationLimits;
import com.prediction.socialnetwork.network.CliqueEnumerationResult;
import com.prediction.socialnetwork.network.CliqueEnumerationStatus;
import com.prediction.socialnetwork.network.LouvainQualitySummary;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SocialNetworkGroupInsightsServiceTest {

    @TempDir
    Path tempDir;

    private final CliqueEnumerationWorkflowService cliques =
            mock(CliqueEnumerationWorkflowService.class);
    private final LouvainAnalysisWorkflowService louvain =
            mock(LouvainAnalysisWorkflowService.class);
    private final SocialNetworkWorkspaceService workspace =
            mock(SocialNetworkWorkspaceService.class);
    private final SocialNetworkGroupInsightsService service =
            new SocialNetworkGroupInsightsService(cliques, louvain, workspace);

    @Test
    void incompleteCliqueNeverLoadsProfilesOrProducesSummary() {
        CliqueEnumerationWorkflowView view = mock(CliqueEnumerationWorkflowView.class);
        when(cliques.status("dataset", 0, 1)).thenReturn(view);
        when(cliques.completedMemberships("dataset")).thenReturn(Optional.empty());
        assertFalse(service.clique("dataset", 0, 20).available());
        verify(workspace, never()).analysisProfileNames(any());
    }

    @Test
    void doesNotReturnCliqueSummaryWhenResultChangesDuringRequest() {
        CliqueEnumerationWorkflowView first = completeCliqueView(Instant.parse("2026-01-01T00:00:00Z"));
        CliqueEnumerationWorkflowView second = completeCliqueView(Instant.parse("2026-01-01T00:00:01Z"));
        when(cliques.status("dataset", 0, 1)).thenReturn(first, second);
        when(cliques.completedMemberships("dataset")).thenReturn(Optional.of(
                new CliqueEnumerationResult(
                        List.of(List.of("a", "b", "c")), CliqueEnumerationStatus.COMPLETED,
                        CliqueEnumerationLimits.DEFAULT, 50, null)));
        when(workspace.analysisProfileNames("dataset")).thenReturn(Map.of("a", "alice"));
        var result = service.clique("dataset", 0, 20);
        assertFalse(result.available());
        assertEquals("analysis_changed", result.reason());
        assertEquals(Map.of(), result.names());
    }

    @Test
    void refusesCrossCommunitySummaryWhenGraphDoesNotMatchQuality() {
        LouvainAnalysisWorkflowView view = mock(LouvainAnalysisWorkflowView.class);
        when(view.complete()).thenReturn(true);
        when(view.converged()).thenReturn(true);
        when(view.dataFingerprint()).thenReturn("fingerprint");
        when(view.communityByNodeId()).thenReturn(Map.of("a", "C1", "b", "C2"));
        LouvainQualitySummary quality = mock(LouvainQualitySummary.class);
        when(quality.crossCommunityTieCount()).thenReturn(2);
        when(view.quality()).thenReturn(quality);
        when(louvain.status("dataset")).thenReturn(view);
        SocialNetworkGraphResult graph = mock(SocialNetworkGraphResult.class);
        when(graph.edges()).thenReturn(List.of(new NetworkEdge("a", "b", "following")));
        when(workspace.cachedNetworkForAnalysis("dataset", "fingerprint"))
                .thenReturn(Optional.of(graph));
        when(workspace.analysisProfileNames("dataset")).thenReturn(Map.of());
        var result = service.louvain("dataset");
        assertFalse(result.available());
        assertEquals("partition_graph_mismatch", result.reason());
    }

    @Test
    void acceptsMatchingCrossCommunityCountsWithoutLoadingGraph() {
        LouvainAnalysisWorkflowView view = mock(LouvainAnalysisWorkflowView.class);
        when(view.complete()).thenReturn(true);
        when(view.converged()).thenReturn(true);
        when(view.dataFingerprint()).thenReturn("fingerprint");
        when(view.communityByNodeId()).thenReturn(Map.of("a", "C1", "b", "C2"));
        LouvainQualitySummary quality = mock(LouvainQualitySummary.class);
        when(quality.crossCommunityTieCount()).thenReturn(1);
        when(view.quality()).thenReturn(quality);
        when(louvain.status("dataset")).thenReturn(view);
        SocialNetworkGraphResult graph = mock(SocialNetworkGraphResult.class);
        when(graph.edges()).thenReturn(List.of(new NetworkEdge("a", "b", "following")));
        when(workspace.cachedNetworkForAnalysis("dataset", "fingerprint"))
                .thenReturn(Optional.of(graph));
        when(workspace.analysisProfileNames("dataset")).thenReturn(Map.of());
        var result = service.louvain("dataset");
        assertTrue(result.available());
        assertEquals(1, result.communityPairs().get(0).tieCount());
        verify(workspace, never()).getNetworkForAnalysis(any(), any());
    }

    @Test
    void restoresCommunityPairFindingsWithoutAnInMemoryGraph() {
        SocialNetworkAnalysisResultStore store =
                new SocialNetworkAnalysisResultStore(
                        new ObjectMapper().findAndRegisterModules(), tempDir);
        LouvainAnalysisWorkflowView view = completeLouvainView();
        when(louvain.status("dataset")).thenReturn(view);

        SocialNetworkGraphResult graph = mock(SocialNetworkGraphResult.class);
        when(graph.edges()).thenReturn(List.of(new NetworkEdge("a", "b", "following")));
        when(workspace.cachedNetworkForAnalysis("dataset", "fingerprint"))
                .thenReturn(Optional.of(graph));
        when(workspace.analysisProfileNames("dataset")).thenReturn(Map.of());

        SocialNetworkGroupInsightsService initialService =
                new SocialNetworkGroupInsightsService(
                        cliques, louvain, workspace, store);
        assertTrue(initialService.louvain("dataset").available());

        SocialNetworkWorkspaceService restartedWorkspace =
                mock(SocialNetworkWorkspaceService.class);
        when(restartedWorkspace.analysisProfileNames("dataset")).thenReturn(Map.of());
        SocialNetworkGroupInsightsService restartedService =
                new SocialNetworkGroupInsightsService(
                        cliques, louvain, restartedWorkspace, store);

        var restored = restartedService.louvain("dataset");
        assertTrue(restored.available());
        assertEquals(1, restored.communityPairs().get(0).tieCount());
        verify(restartedWorkspace, never())
                .cachedNetworkForAnalysis("dataset", "fingerprint");
        verify(restartedWorkspace, never()).getGroupEvidenceInput(any(), any());
    }

    private static LouvainAnalysisWorkflowView completeLouvainView() {
        LouvainAnalysisWorkflowView view = mock(LouvainAnalysisWorkflowView.class);
        when(view.complete()).thenReturn(true);
        when(view.converged()).thenReturn(true);
        when(view.dataFingerprint()).thenReturn("fingerprint");
        when(view.cacheKey()).thenReturn("analysis-key");
        when(view.datasetId()).thenReturn(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
        when(view.datasetVersion()).thenReturn(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION);
        when(view.communityByNodeId()).thenReturn(Map.of("a", "C1", "b", "C2"));
        when(view.quality()).thenReturn(new LouvainQualitySummary(
                2, 2, 2, List.of(), 2, 1, 1, Map.of(), 0.0));
        return view;
    }

    private static CliqueEnumerationWorkflowView completeCliqueView(Instant finished) {
        CliqueEnumerationWorkflowView view = mock(CliqueEnumerationWorkflowView.class);
        when(view.complete()).thenReturn(true);
        when(view.membershipListComplete()).thenReturn(true);
        when(view.dataFingerprint()).thenReturn("fingerprint");
        when(view.cacheKey()).thenReturn("key");
        when(view.finishedAt()).thenReturn(finished);
        return view;
    }
}
