package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.network.NetworkMetrics;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SocialNetworkEvidenceFactSummaryServiceTest {

    @Autowired
    private SocialNetworkWorkspaceService workspace;

    private final SocialNetworkEvidenceFactSummaryService summaryService =
            new SocialNetworkEvidenceFactSummaryService();

    @Test
    void cachesOnlyMatchingDatasetAndAlgorithmVersions() {
        SocialNetworkWorkspaceSnapshot snapshot =
                workspace.get("think-tank-social-network");

        SocialNetworkEvidenceFactSummaryGenerator.Summary first =
                summaryService.get(snapshot);
        assertSame(first, summaryService.get(snapshot));

        SocialNetworkWorkspaceSnapshot changedDatasetVersion =
                withDatasetVersion(snapshot, "v-next");
        SocialNetworkEvidenceFactSummaryGenerator.Summary changedDatasetSummary =
                summaryService.get(changedDatasetVersion);
        assertNotSame(first, changedDatasetSummary);
        assertEquals(
                "v-next",
                changedDatasetSummary.analysisSnapshot().get("datasetVersion"));

        SocialNetworkWorkspaceSnapshot changedMetricVersion =
                withMetricVersion(snapshot, "network-metrics-test-next");
        SocialNetworkEvidenceFactSummaryGenerator.Summary changedMetricSummary =
                summaryService.get(changedMetricVersion);
        assertNotSame(first, changedMetricSummary);
        @SuppressWarnings("unchecked")
        var versions = (java.util.Map<String, Object>)
                changedMetricSummary.analysisSnapshot().get("versions");
        assertEquals(
                "network-metrics-test-next",
                versions.get("networkMetricVersion"));
    }

    @Test
    void refusesUploadSnapshotsBeforeTheyCanEnterTheSummaryCache() {
        SocialNetworkWorkspaceSnapshot snapshot =
                workspace.get("think-tank-social-network");
        SocialNetworkDataset builtin = snapshot.dataset();
        SocialNetworkDataset uploadDataset = new SocialNetworkDataset(
                "upload-session-test",
                builtin.name(),
                DatasetType.UPLOAD_SESSION,
                "session-test",
                builtin.status(),
                builtin.description(),
                builtin.sourceDescription(),
                builtin.timeRangeFrom(),
                builtin.timeRangeTo(),
                builtin.files(),
                "test-session",
                builtin.normalizationVersion(),
                builtin.metricVersion(),
                builtin.textProcessingVersion(),
                builtin.explanationVersion());
        SocialNetworkWorkspaceSnapshot uploadSnapshot = new SocialNetworkWorkspaceSnapshot(
                uploadDataset,
                snapshot.quality(),
                snapshot.capabilities(),
                snapshot.network(),
                snapshot.interactions(),
                snapshot::textAnalysis,
                snapshot.users(),
                snapshot.relationships(),
                snapshot.tweets());

        assertThrows(
                SocialNetworkEvidenceSummaryDatasetNotSupportedException.class,
                () -> summaryService.get(uploadSnapshot));
    }

    @Test
    void preservesEmptyAndPartialStatesInCachedResults() {
        SocialNetworkWorkspaceSnapshot emptySnapshot = withDatasetVersion(
                SocialNetworkEvidenceFactSummaryGeneratorTest.smallSnapshot(true, true),
                "empty-version");
        SocialNetworkWorkspaceSnapshot partialSnapshot = withDatasetVersion(
                SocialNetworkEvidenceFactSummaryGeneratorTest.smallSnapshot(false),
                "partial-version");

        assertEquals("empty", summaryService.get(emptySnapshot).state());
        assertEquals("limited", summaryService.get(partialSnapshot).state());
    }

    private static SocialNetworkWorkspaceSnapshot withDatasetVersion(
            SocialNetworkWorkspaceSnapshot snapshot, String datasetVersion) {
        SocialNetworkDataset source = snapshot.dataset();
        SocialNetworkDataset dataset = new SocialNetworkDataset(
                source.datasetId(),
                source.name(),
                source.type(),
                datasetVersion,
                source.status(),
                source.description(),
                source.sourceDescription(),
                source.timeRangeFrom(),
                source.timeRangeTo(),
                source.files(),
                source.sessionId(),
                source.normalizationVersion(),
                source.metricVersion(),
                source.textProcessingVersion(),
                source.explanationVersion());
        return withDatasetAndMetrics(snapshot, dataset, snapshot.network().metrics());
    }

    private static SocialNetworkWorkspaceSnapshot withMetricVersion(
            SocialNetworkWorkspaceSnapshot snapshot, String metricVersion) {
        NetworkMetrics source = snapshot.network().metrics();
        NetworkMetrics metrics = new NetworkMetrics(
                source.nodeCount(),
                source.edgeCount(),
                source.profileUserCount(),
                source.externalNodeCount(),
                source.isolatedNodeCount(),
                source.weaklyConnectedComponentCount(),
                source.largestWeaklyConnectedComponentNodeCount(),
                source.largestWeaklyConnectedComponentRatio(),
                source.averageClusteringCoefficient(),
                source.reciprocityRate(),
                source.largestWeaklyConnectedComponentAveragePathLength(),
                source.density(),
                metricVersion,
                source.pagerankVersion(),
                source.includeExternalNodes(),
                source.excludeSelfLoops());
        return withDatasetAndMetrics(snapshot, snapshot.dataset(), metrics);
    }

    private static SocialNetworkWorkspaceSnapshot withDatasetAndMetrics(
            SocialNetworkWorkspaceSnapshot snapshot,
            SocialNetworkDataset dataset,
            NetworkMetrics metrics) {
        SocialNetworkGraphResult source = snapshot.network();
        SocialNetworkGraphResult graph = new SocialNetworkGraphResult(
                source.nodes(),
                source.edges(),
                metrics,
                source.pageRank(),
                source.pageRankRanking(),
                source.communityDetection(),
                source.structuralRoles(),
                source.calculatedAt());
        return new SocialNetworkWorkspaceSnapshot(
                dataset,
                snapshot.quality(),
                snapshot.capabilities(),
                graph,
                snapshot.interactions(),
                snapshot::textAnalysis,
                snapshot.users(),
                snapshot.relationships(),
                snapshot.tweets());
    }
}
