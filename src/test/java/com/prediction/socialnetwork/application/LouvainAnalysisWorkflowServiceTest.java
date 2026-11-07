package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prediction.socialnetwork.cache.SocialNetworkAnalysisCacheKeyFactory;
import com.prediction.socialnetwork.cache.SocialNetworkAnalysisResultCache;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.isolation.LouvainWorkerRunResult;
import com.prediction.socialnetwork.network.CommunityDetectionResult;
import com.prediction.socialnetwork.network.CommunityDetectionStatus;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
import com.prediction.socialnetwork.network.LouvainCommunityDetector;
import com.prediction.socialnetwork.network.LouvainQualityCalculator;
import com.prediction.socialnetwork.network.LouvainQualitySummary;
import com.prediction.socialnetwork.network.NetworkCommunity;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class LouvainAnalysisWorkflowServiceTest {

    @Test
    void startsExplicitlyCachesTheDefaultBaselineAndSeparatesChangedInput() throws Exception {
        Fixture fixture = fixture(true);
        try {
            LouvainAnalysisWorkflowView idle =
                    fixture.service().status(fixture.dataset().datasetId());
            assertEquals("NOT_REQUESTED", idle.state());
            verifyNoInteractions(fixture.workspace());

            fixture.service().request(fixture.dataset().datasetId());
            Awaitility.await()
                    .atMost(java.time.Duration.ofSeconds(2))
                    .untilAsserted(() -> assertEquals(
                            "COMPLETED",
                            fixture.service().status(fixture.dataset().datasetId()).state()));

            LouvainAnalysisWorkflowView completed =
                    fixture.service().request(fixture.dataset().datasetId());
            assertTrue(completed.complete());
            assertTrue(completed.workerCompleted());
            assertTrue(completed.converged());
            assertTrue(completed.cacheHit());
            assertEquals(4, completed.inputNodeCount());
            assertEquals(3, completed.inputDirectedRelationshipCount());
            assertEquals(1, completed.unassignedNodeCount());
            assertEquals(List.of("isolated"), completed.unassignedNodeIds());
            assertEquals(3, completed.communityByNodeId().size());
            assertEquals(1, completed.communities().size());
            assertEquals(3, completed.quality().projectedUndirectedTieCount());
            assertEquals(1.0, completed.algorithmParameters().get("resolution"));
            assertEquals("unweighted", completed.algorithmParameters().get("weighting"));
            assertEquals(
                    "not_applicable",
                    completed.algorithmParameters().get("randomSeed"));
            assertEquals(
                    SocialNetworkAnalysisCacheKeyFactory.LOUVAIN_COMPLETENESS_CONTRACT,
                    completed.algorithmParameters().get("completenessContract"));
            assertEquals(1, fixture.runnerCalls().get());
            verify(fixture.workspace()).getNetworkForAnalysis(
                    fixture.dataset().datasetId(),
                    "fingerprint-a");

            fixture.currentFingerprint().set(
                    fingerprint(fixture.dataset(), "fingerprint-b"));
            LouvainAnalysisWorkflowView changed =
                    fixture.service().status(fixture.dataset().datasetId());
            assertEquals("NOT_REQUESTED", changed.state());
            assertEquals("fingerprint-b", changed.dataFingerprint());
            assertFalse(changed.cacheHit());
            assertEquals(1, fixture.runnerCalls().get());
        } finally {
            fixture.service().shutdown();
        }
    }

    @Test
    void completedButNonConvergedPartitionIsNeverMarkedComplete() throws Exception {
        Fixture fixture = fixture(false);
        try {
            fixture.service().request(fixture.dataset().datasetId());
            Awaitility.await()
                    .atMost(java.time.Duration.ofSeconds(2))
                    .untilAsserted(() -> assertEquals(
                            "COMPLETED_UNCONVERGED",
                            fixture.service().status(fixture.dataset().datasetId()).state()));

            LouvainAnalysisWorkflowView result =
                    fixture.service().status(fixture.dataset().datasetId());
            assertTrue(result.workerCompleted());
            assertFalse(result.converged());
            assertFalse(result.complete());
            assertTrue(result.cacheHit());
            assertEquals(1, result.communities().size());
        } finally {
            fixture.service().shutdown();
        }
    }

    @Test
    void memoryPreflightRejectsBeforeLoadingTheAnalysisGraph() throws Exception {
        Fixture fixture = fixture(false, () -> {
            throw new IllegalStateException(
                    "Analysis refused: available physical memory is below the 2 GiB safety threshold");
        });
        try {
            fixture.service().request(fixture.dataset().datasetId());
            Awaitility.await()
                    .atMost(java.time.Duration.ofSeconds(2))
                    .untilAsserted(() -> assertEquals(
                            "MEMORY_LIMIT",
                            fixture.service().status(fixture.dataset().datasetId()).state()));
            assertEquals(
                    "physical_memory_preflight",
                    fixture.service().status(fixture.dataset().datasetId())
                            .terminationDetail());
            verifyNoInteractions(fixture.workspace());
            assertEquals(0, fixture.runnerCalls().get());
        } finally {
            fixture.service().shutdown();
        }
    }

    @Test
    void restoresPersistedCompleteResultWithoutStartingTheRunner() throws Exception {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkDataset dataset = registry.findDataset(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
        SocialNetworkDataFingerprintService fingerprintService =
                mock(SocialNetworkDataFingerprintService.class);
        SocialNetworkDataFingerprintService.Fingerprint fingerprint =
                fingerprint(dataset, "persisted-louvain-data");
        when(fingerprintService.fingerprint(dataset)).thenReturn(fingerprint);
        SocialNetworkAnalysisCacheKeyFactory keyFactory =
                new SocialNetworkAnalysisCacheKeyFactory();
        var identity = keyFactory.louvain(
                dataset, fingerprint, LouvainAnalysisParameters.DEFAULT);
        SocialNetworkAnalysisResultStore resultStore =
                mock(SocialNetworkAnalysisResultStore.class);
        Instant now = Instant.now();
        LouvainAnalysisWorkflowView persisted = new LouvainAnalysisWorkflowView(
                dataset.datasetId(),
                dataset.datasetVersion(),
                "COMPLETED",
                true,
                true,
                true,
                "COMPLETED",
                null,
                now.minusSeconds(5),
                now,
                3,
                3,
                LouvainCommunityDetector.ALGORITHM_VERSION,
                identity.parameters(),
                SocialNetworkAnalysisCacheKeyFactory.ANALYSIS_SCOPE,
                fingerprint.dataFingerprint(),
                fingerprint.files(),
                fingerprint.inputContractVersion(),
                fingerprint.relationProjectionVersion(),
                LouvainAnalysisParameters.DEFAULT.relationProjection().resultName(),
                LouvainQualityCalculator.QUALITY_CONTRACT_VERSION,
                List.of(new NetworkCommunity("C001", 3, 3, 0, 2)),
                Map.of("a", "C001", "b", "C001", "c", "C001"),
                0,
                List.of(),
                5,
                new LouvainQualitySummary(
                        3, 3, 3, List.of(), 3, 2, 1, Map.of("C001", 2), 0.25),
                50,
                1024,
                2048,
                128,
                256,
                identity.cacheKey().value(),
                false);
        when(resultStore.loadLouvain(identity.cacheKey().value()))
                .thenReturn(java.util.Optional.of(persisted));
        AtomicInteger runnerCalls = new AtomicInteger();
        SocialNetworkWorkspaceService workspace = mock(SocialNetworkWorkspaceService.class);
        LouvainAnalysisWorkflowService service = new LouvainAnalysisWorkflowService(
                registry,
                workspace,
                (nodes, edges, parameters, cancelled) -> {
                    runnerCalls.incrementAndGet();
                    return null;
                },
                fingerprintService,
                keyFactory,
                new SocialNetworkAnalysisResultCache(),
                () -> { },
                resultStore);

        try {
            LouvainAnalysisWorkflowView restored = service.status(dataset.datasetId());
            assertEquals("COMPLETED", restored.state());
            assertTrue(restored.cacheHit());
            assertEquals(3, restored.communityByNodeId().size());
            assertEquals(0, runnerCalls.get());
            verifyNoInteractions(workspace);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void springContextWiresProductionRunnerWithoutStartingAnalysis() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    SocialNetworkAnalysisResultStore.class,
                    SocialNetworkAnalysisResultStore::disabled);
            context.register(
                    SocialNetworkDatasetRegistry.class,
                    SocialNetworkWorkspaceService.class,
                    SocialNetworkDataFingerprintService.class,
                    SocialNetworkAnalysisCacheKeyFactory.class,
                    SocialNetworkAnalysisResultCache.class,
                    com.prediction.socialnetwork.isolation.WindowsLouvainAnalysisRunner.class,
                    LouvainAnalysisWorkflowService.class);
            context.refresh();

            LouvainAnalysisWorkflowView idle = context
                    .getBean(LouvainAnalysisWorkflowService.class)
                    .status(SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
            assertEquals("NOT_REQUESTED", idle.state());
        }
    }

    private static Fixture fixture(boolean converged) throws Exception {
        return fixture(converged, () -> { });
    }

    private static Fixture fixture(boolean converged, Runnable resourcePreflight)
            throws Exception {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkDataset dataset = registry.findDataset(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
        SocialNetworkDataFingerprintService fingerprintService =
                mock(SocialNetworkDataFingerprintService.class);
        AtomicReference<SocialNetworkDataFingerprintService.Fingerprint> currentFingerprint =
                new AtomicReference<>(fingerprint(dataset, "fingerprint-a"));
        when(fingerprintService.fingerprint(dataset))
                .thenAnswer(invocation -> currentFingerprint.get());

        List<NetworkNode> nodes = List.of(
                node("a"), node("b"), node("c"), node("isolated"));
        List<NetworkEdge> edges = List.of(
                new NetworkEdge("a", "b", "following"),
                new NetworkEdge("b", "c", "following"),
                new NetworkEdge("c", "a", "following"));
        SocialNetworkWorkspaceService workspace = mock(SocialNetworkWorkspaceService.class);
        SocialNetworkGraphResult graph = new SocialNetworkGraphResult(
                nodes, edges, null, null, List.of(), null, null, Instant.now());
        when(workspace.getNetworkForAnalysis(dataset.datasetId(), "fingerprint-a"))
                .thenReturn(graph);

        AtomicInteger runnerCalls = new AtomicInteger();
        Map<String, String> assignments = Map.of(
                "a", "C001", "b", "C001", "c", "C001");
        LouvainQualitySummary quality = new LouvainQualityCalculator().calculate(
                nodes, edges, assignments, LouvainAnalysisParameters.DEFAULT);
        CommunityDetectionResult detection = new CommunityDetectionResult(
                List.of(new NetworkCommunity("C001", 3, 3, 0, 3)),
                assignments,
                1,
                2,
                converged,
                LouvainCommunityDetector.ALGORITHM,
                LouvainAnalysisParameters.DEFAULT.relationProjection().resultName(),
                CommunityDetectionStatus.COMPLETED);
        LouvainAnalysisExecutor runner = (actualNodes, actualEdges, parameters, cancelled) -> {
            runnerCalls.incrementAndGet();
            return new LouvainWorkerRunResult(
                    LouvainWorkerRunResult.Status.COMPLETED,
                    LouvainCommunityDetector.ALGORITHM_VERSION,
                    parameters.resolution(),
                    parameters,
                    detection,
                    quality,
                    null,
                    42,
                    1024,
                    2048,
                    128,
                    256);
        };
        LouvainAnalysisWorkflowService service = new LouvainAnalysisWorkflowService(
                registry,
                workspace,
                runner,
                fingerprintService,
                new SocialNetworkAnalysisCacheKeyFactory(),
                new SocialNetworkAnalysisResultCache(),
                resourcePreflight);
        return new Fixture(
                dataset, workspace, currentFingerprint, runnerCalls, service);
    }

    private static SocialNetworkDataFingerprintService.Fingerprint fingerprint(
            SocialNetworkDataset dataset, String digest) {
        return new SocialNetworkDataFingerprintService.Fingerprint(
                dataset.datasetId(),
                dataset.datasetVersion(),
                dataset.normalizationVersion(),
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                List.of(
                        new SocialNetworkDataFingerprintService.FileFingerprint(
                                "users", "users.csv", 1, "a".repeat(64)),
                        new SocialNetworkDataFingerprintService.FileFingerprint(
                                "relationships", "relationships.csv", 2, "b".repeat(64))),
                digest);
    }

    private static NetworkNode node(String id) {
        return new NetworkNode(id, NetworkNodeType.PROFILE, 0, 0, 0.0, false);
    }

    private record Fixture(
            SocialNetworkDataset dataset,
            SocialNetworkWorkspaceService workspace,
            AtomicReference<SocialNetworkDataFingerprintService.Fingerprint> currentFingerprint,
            AtomicInteger runnerCalls,
            LouvainAnalysisWorkflowService service) {
    }
}
