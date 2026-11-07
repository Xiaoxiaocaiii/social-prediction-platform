package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prediction.socialnetwork.cache.SocialNetworkAnalysisCacheKeyFactory;
import com.prediction.socialnetwork.cache.SocialNetworkAnalysisResultCache;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.network.CliqueEnumerationLimits;
import com.prediction.socialnetwork.network.CliqueEnumerationResult;
import com.prediction.socialnetwork.network.CliqueEnumerationStatus;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.isolation.WindowsCliqueEnumerationRunner;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class CliqueEnumerationWorkflowServiceTest {

    @Test
    void startsOnceReusesTheResultAndPassesTheEntireAnalysisGraph() throws Exception {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = mock(SocialNetworkWorkspaceService.class);
        SocialNetworkDataFingerprintService fingerprintService =
                mock(SocialNetworkDataFingerprintService.class);
        List<NetworkNode> nodes = List.of(
                node("a"), node("b"), node("c"), node("isolated"));
        List<NetworkEdge> edges = List.of(
                new NetworkEdge("a", "b", "following"),
                new NetworkEdge("b", "c", "following"),
                new NetworkEdge("c", "a", "following"),
                new NetworkEdge("isolated", "a", "following"));
        SocialNetworkDataset dataset = registry.findDataset(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
        SocialNetworkDataFingerprintService.Fingerprint fingerprint =
                fingerprint(dataset, "fixture-data-fingerprint");
        AtomicReference<SocialNetworkDataFingerprintService.Fingerprint> currentFingerprint =
                new AtomicReference<>(fingerprint);
        when(fingerprintService.fingerprint(dataset))
                .thenAnswer(invocation -> currentFingerprint.get());
        SocialNetworkGraphResult graph = new SocialNetworkGraphResult(
                nodes, edges, null, null, List.of(), null, null, Instant.now());
        when(workspace.getNetworkForAnalysis(
                        SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                        fingerprint.dataFingerprint()))
                .thenReturn(graph);

        CountDownLatch runnerStarted = new CountDownLatch(1);
        CountDownLatch allowRunnerToFinish = new CountDownLatch(1);
        AtomicInteger runnerCalls = new AtomicInteger();
        AtomicReference<List<NetworkNode>> receivedNodes = new AtomicReference<>();
        AtomicReference<List<NetworkEdge>> receivedEdges = new AtomicReference<>();
        CliqueEnumerationExecutor runner = (actualNodes, actualEdges, cancelled) -> {
            runnerCalls.incrementAndGet();
            receivedNodes.set(actualNodes);
            receivedEdges.set(actualEdges);
            runnerStarted.countDown();
            try {
                if (!allowRunnerToFinish.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test runner release timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return new CliqueEnumerationResult(
                    List.of(List.of("a", "b", "c")),
                    CliqueEnumerationStatus.COMPLETED,
                    CliqueEnumerationLimits.DEFAULT,
                    13,
                    null);
        };
        CliqueEnumerationWorkflowService service =
                newService(registry, workspace, runner, fingerprintService);

        try {
            CliqueEnumerationWorkflowView first = service.request(dataset.datasetId());
            assertEquals("RUNNING", first.state());
            assertTrue(runnerStarted.await(2, TimeUnit.SECONDS));

            CliqueEnumerationWorkflowView duplicate = service.request(dataset.datasetId());
            assertEquals("RUNNING", duplicate.state());
            assertEquals(1, runnerCalls.get());

            allowRunnerToFinish.countDown();
            Awaitility.await()
                    .atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(
                            "COMPLETED",
                            service.status(dataset.datasetId(), 0, 1).state()));

            CliqueEnumerationWorkflowView reused =
                    service.request(dataset.datasetId());
            assertEquals("COMPLETED", reused.state());
            assertTrue(reused.complete());
            assertEquals(1, reused.retainedCliqueCount());
            assertEquals(1, reused.enumeratedCliqueCount());
            assertEquals(List.of(List.of("a", "b", "c")), reused.cliques());
            assertEquals(java.util.Map.of(3, 1), reused.cliqueSizeDistribution());
            assertEquals(
                    java.util.Map.of("a", 1, "b", 1, "c", 1),
                    reused.nodeParticipationCounts());
            assertTrue(reused.membershipListComplete());
            assertEquals(4, reused.inputNodeCount());
            assertEquals(4, reused.inputDirectedEdgeCount());
            assertEquals(nodes, receivedNodes.get());
            assertEquals(edges, receivedEdges.get());
            assertEquals("data_fingerprint", reused.inputIdentityMode());
            assertTrue(reused.inputFingerprintAvailable());
            assertEquals(fingerprint.dataFingerprint(), reused.dataFingerprint());
            assertEquals(fingerprint.files(), reused.inputFiles());
            assertTrue(reused.cacheHit());
            assertEquals(1, service.status(dataset.datasetId(), 0, 1).cliques().size());
            assertEquals(1, runnerCalls.get());

            currentFingerprint.set(fingerprint(dataset, "changed-data-fingerprint"));
            CliqueEnumerationWorkflowView changedInput =
                    service.status(dataset.datasetId(), 0, 1);
            assertEquals("NOT_REQUESTED", changedInput.state());
            assertEquals("changed-data-fingerprint", changedInput.dataFingerprint());
            assertFalse(changedInput.cacheHit());
            assertEquals(1, runnerCalls.get());
        } finally {
            allowRunnerToFinish.countDown();
            service.shutdown();
        }
    }

    @Test
    void rejectsDatasetsOutsideTheBuiltInWorkflowBeforeLoadingOrRunning() throws Exception {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = mock(SocialNetworkWorkspaceService.class);
        SocialNetworkDataFingerprintService fingerprintService =
                mock(SocialNetworkDataFingerprintService.class);
        SocialNetworkDataset dataset = registry.findDataset(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
        when(fingerprintService.fingerprint(dataset))
                .thenReturn(fingerprint(dataset, "fixture-data-fingerprint"));
        AtomicInteger runnerCalls = new AtomicInteger();
        CliqueEnumerationWorkflowService service =
                newService(
                        registry,
                        workspace,
                        (nodes, edges, cancelled) -> {
                            runnerCalls.incrementAndGet();
                            return null;
                        },
                        fingerprintService);

        try {
            CliqueEnumerationWorkflowView idle = service.status(
                    SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID, 0, 20);
            assertEquals("NOT_REQUESTED", idle.state());
            verifyNoInteractions(workspace);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.request("not-the-built-in-dataset"));
            verifyNoInteractions(workspace);
            assertEquals(0, runnerCalls.get());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void cachedLimitedResultRemainsExplicitlyIncomplete() throws Exception {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkDataset dataset = registry.findDataset(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
        SocialNetworkDataFingerprintService fingerprintService =
                mock(SocialNetworkDataFingerprintService.class);
        SocialNetworkDataFingerprintService.Fingerprint fingerprint =
                fingerprint(dataset, "limited-result-fingerprint");
        when(fingerprintService.fingerprint(dataset)).thenReturn(fingerprint);

        SocialNetworkWorkspaceService workspace = mock(SocialNetworkWorkspaceService.class);
        SocialNetworkGraphResult graph = new SocialNetworkGraphResult(
                List.of(node("a")), List.of(), null, null, List.of(), null, null,
                Instant.now());
        when(workspace.getNetworkForAnalysis(dataset.datasetId(), fingerprint.dataFingerprint()))
                .thenReturn(graph);

        CliqueEnumerationWorkflowService service = newService(
                registry,
                workspace,
                (nodes, edges, cancelled) -> CliqueEnumerationResult.externalTermination(
                        CliqueEnumerationStatus.TIME_LIMIT,
                        com.prediction.socialnetwork.network.CliqueEnumerationWorkerProtocol
                                .DEFAULT_WORKER_LIMITS,
                        "time_limit"),
                fingerprintService);
        try {
            service.request(dataset.datasetId());
            Awaitility.await()
                    .atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(
                            "TIME_LIMIT",
                            service.status(dataset.datasetId(), 0, 20).state()));

            CliqueEnumerationWorkflowView cached =
                    service.status(dataset.datasetId(), 0, 20);
            assertTrue(cached.cacheHit());
            assertFalse(cached.complete());
            assertFalse(cached.membershipListComplete());
            assertEquals("TIME_LIMIT", cached.state());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void memoryPreflightRejectsBeforeLoadingTheAnalysisGraph() throws Exception {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkDataset dataset = registry.findDataset(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
        SocialNetworkWorkspaceService workspace = mock(SocialNetworkWorkspaceService.class);
        SocialNetworkDataFingerprintService fingerprintService =
                mock(SocialNetworkDataFingerprintService.class);
        when(fingerprintService.fingerprint(dataset))
                .thenReturn(fingerprint(dataset, "preflight-data-fingerprint"));
        AtomicInteger runnerCalls = new AtomicInteger();
        CliqueEnumerationWorkflowService service = new CliqueEnumerationWorkflowService(
                registry,
                workspace,
                (nodes, edges, cancelled) -> {
                    runnerCalls.incrementAndGet();
                    return null;
                },
                fingerprintService,
                new SocialNetworkAnalysisCacheKeyFactory(),
                new SocialNetworkAnalysisResultCache(),
                () -> {
                    throw new IllegalStateException(
                            "Analysis refused: available physical memory is below the 2 GiB safety threshold");
                });

        try {
            service.request(dataset.datasetId());
            Awaitility.await()
                    .atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(
                            "MEMORY_LIMIT",
                            service.status(dataset.datasetId(), 0, 20).state()));
            assertEquals("physical_memory_preflight",
                    service.status(dataset.datasetId(), 0, 20).terminationDetail());
            verifyNoInteractions(workspace);
            assertEquals(0, runnerCalls.get());
        } finally {
            service.shutdown();
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
                fingerprint(dataset, "persisted-data-fingerprint");
        when(fingerprintService.fingerprint(dataset)).thenReturn(fingerprint);
        SocialNetworkAnalysisCacheKeyFactory keyFactory =
                new SocialNetworkAnalysisCacheKeyFactory();
        var identity = keyFactory.clique(dataset, fingerprint);
        SocialNetworkAnalysisResultStore resultStore =
                mock(SocialNetworkAnalysisResultStore.class);
        Instant now = Instant.now();
        CliqueEnumerationWorkflowView persisted = new CliqueEnumerationWorkflowView(
                dataset.datasetId(),
                dataset.datasetVersion(),
                "COMPLETED",
                true,
                null,
                now.minusSeconds(5),
                now,
                3,
                3,
                1,
                1,
                3,
                java.util.Map.of(3, 1),
                java.util.Map.of("a", 1, "b", 1, "c", 1),
                true,
                0,
                1,
                List.of(List.of("a", "b", "c")),
                com.prediction.socialnetwork.network.CliqueEnumerationWorkerProtocol
                        .DEFAULT_WORKER_LIMITS.timeLimit().toMillis(),
                com.prediction.socialnetwork.network.CliqueEnumerationWorkerProtocol
                        .DEFAULT_WORKER_LIMITS.maxCliques(),
                com.prediction.socialnetwork.network.CliqueEnumerationWorkerProtocol
                        .DEFAULT_WORKER_LIMITS.maxCliqueArrayBytes(),
                "data_fingerprint",
                true,
                identity.projection(),
                SocialNetworkAnalysisCacheKeyFactory.ANALYSIS_SCOPE,
                fingerprint.dataFingerprint(),
                fingerprint.files(),
                fingerprint.inputContractVersion(),
                fingerprint.relationProjectionVersion(),
                String.valueOf(identity.parameters().get("algorithmVersion")),
                identity.parameters(),
                identity.cacheKey().value(),
                false);
        when(resultStore.loadClique(identity.cacheKey().value()))
                .thenReturn(java.util.Optional.of(persisted));
        AtomicInteger runnerCalls = new AtomicInteger();
        SocialNetworkWorkspaceService workspace = mock(SocialNetworkWorkspaceService.class);
        CliqueEnumerationWorkflowService service = new CliqueEnumerationWorkflowService(
                registry,
                workspace,
                (nodes, edges, cancelled) -> {
                    runnerCalls.incrementAndGet();
                    return null;
                },
                fingerprintService,
                keyFactory,
                new SocialNetworkAnalysisResultCache(),
                () -> { },
                resultStore);

        try {
            CliqueEnumerationWorkflowView restored =
                    service.status(dataset.datasetId(), 0, 1);
            assertEquals("COMPLETED", restored.state());
            assertTrue(restored.cacheHit());
            assertEquals(List.of(List.of("a", "b", "c")), restored.cliques());
            assertTrue(restored.membershipListComplete());
            assertEquals(0, runnerCalls.get());
            verifyNoInteractions(workspace);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void springContainerWiresTheProductionRunnerWithoutStartingAnalysis() {
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
                    WindowsCliqueEnumerationRunner.class,
                    CliqueEnumerationWorkflowService.class);
            context.refresh();

            CliqueEnumerationWorkflowView idle = context
                    .getBean(CliqueEnumerationWorkflowService.class)
                    .status(SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID, 0, 20);
            assertEquals("NOT_REQUESTED", idle.state());
        }
    }

    private static NetworkNode node(String id) {
        return new NetworkNode(id, NetworkNodeType.PROFILE, 0, 0, 0.0, false);
    }

    private static CliqueEnumerationWorkflowService newService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            CliqueEnumerationExecutor runner,
            SocialNetworkDataFingerprintService fingerprintService) {
        return new CliqueEnumerationWorkflowService(
                registry,
                workspace,
                runner,
                fingerprintService,
                new SocialNetworkAnalysisCacheKeyFactory(),
                new SocialNetworkAnalysisResultCache(),
                () -> { });
    }

    private static SocialNetworkDataFingerprintService.Fingerprint fingerprint(
            SocialNetworkDataset dataset, String value) {
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
                value);
    }
}
