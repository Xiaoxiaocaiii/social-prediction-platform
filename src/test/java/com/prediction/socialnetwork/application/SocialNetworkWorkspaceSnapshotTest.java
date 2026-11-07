package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.prediction.socialnetwork.network.CommunityDetectionStatus;
import com.prediction.socialnetwork.text.SocialNetworkTextAnalysis;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SocialNetworkWorkspaceSnapshotTest {

    @Test
    void computesTextAnalysisOnlyWhenRequestedAndCachesTheResult() {
        AtomicInteger analysisCount = new AtomicInteger();
        SocialNetworkTextAnalysis.Result expected =
                new SocialNetworkTextAnalysis().analyze(List.of());
        SocialNetworkWorkspaceSnapshot snapshot = new SocialNetworkWorkspaceSnapshot(
                null,
                null,
                null,
                null,
                null,
                () -> {
                    analysisCount.incrementAndGet();
                    return expected;
                },
                List.of(),
                List.of(),
                List.of());

        assertEquals(0, analysisCount.get());
        assertSame(expected, snapshot.textAnalysis());
        assertSame(expected, snapshot.textAnalysis());
        assertEquals(1, analysisCount.get());
    }

    @Test
    void buildsAndCachesTheBuiltinAnalysisGraphWithoutLoadingTweetAnalysis() {
        SocialNetworkWorkspaceService workspace =
                new SocialNetworkWorkspaceService(new SocialNetworkDatasetRegistry());

        var graph = workspace.getNetworkForAnalysis(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID, "fixture-fingerprint");

        assertEquals(626, graph.nodes().size());
        assertEquals(12963, graph.edges().size());
        assertEquals(CommunityDetectionStatus.NOT_REQUESTED,
                graph.communityDetection().status());
        assertSame(
                graph,
                workspace.getNetworkForAnalysis(
                        SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID, "fixture-fingerprint"));
    }
}
