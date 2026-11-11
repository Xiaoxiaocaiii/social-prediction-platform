package com.prediction.socialnetwork.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.network.CommunityDetectionStatus;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
import com.prediction.socialnetwork.network.LouvainAnalysisWorkerMain;
import com.prediction.socialnetwork.network.LouvainCommunityDetector;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WindowsLouvainAnalysisRunnerTest {

    private static final Path JAVA_EXECUTABLE =
            Path.of(System.getProperty("java.home"), "bin", "javaw.exe");
    private static final String WORKER_CLASS_PATH = System.getProperty(
            "surefire.test.class.path",
            System.getProperty("java.class.path"));

    @BeforeAll
    static void requireWindows() {
        Assumptions.assumeTrue(
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows"),
                "Windows Job Object integration tests require Windows");
    }

    @Test
    void runsLouvainInsideTheBoundedWorkerAndReadsVerifiedQuality() {
        WindowsLouvainAnalysisRunner runner = runner(
                WindowsJobLimits.DEFAULT, LouvainAnalysisWorkerMain.class.getName());
        List<NetworkNode> nodes = nodes("A", "B", "C", "D");
        List<NetworkEdge> edges = edges(
                "A", "B", "B", "A", "A", "C", "B", "C", "C", "C");

        LouvainWorkerRunResult result =
                runner.runForControlledVerification(nodes, edges, () -> false);

        assertEquals(LouvainWorkerRunResult.Status.COMPLETED, result.status());
        assertEquals(CommunityDetectionStatus.COMPLETED, result.detection().status());
        assertEquals(4, result.quality().inputNodeCount());
        assertEquals(5, result.quality().inputDirectedRelationshipCount());
        assertEquals(3, result.quality().projectedUndirectedTieCount());
        assertEquals(1, result.quality().unassignedNodeCount());
        assertEquals(LouvainCommunityDetector.ALGORITHM_VERSION, result.algorithmVersion());
        assertEquals(LouvainCommunityDetector.RESOLUTION, result.resolution());
        assertEquals(LouvainAnalysisParameters.DEFAULT, result.parameters());
        assertTrue(result.peakJobMemoryBytes() > 0);
        assertTrue(result.totalCpuTime100Nanoseconds() >= 0);
        assertTrue(result.requestBytes() > 0);
        assertTrue(result.resultArtifactBytes() > 0);
        assertTrue(result.elapsedMillis() < 30_000);
    }

    @Test
    void roundTripsSensitivityParametersAndMutualProjectionThroughWorker() {
        WindowsLouvainAnalysisRunner runner = runner(
                WindowsJobLimits.DEFAULT, LouvainAnalysisWorkerMain.class.getName());
        LouvainAnalysisParameters parameters = new LouvainAnalysisParameters(
                0.5,
                LouvainAnalysisParameters.NodeOrder.DEGREE_ASCENDING_THEN_ID_ASCENDING,
                LouvainAnalysisParameters.RelationProjection.MUTUAL_ONLY);

        LouvainWorkerRunResult result = runner.runForControlledVerification(
                nodes("A", "B", "C"),
                edges("A", "B", "B", "A", "A", "C"),
                parameters,
                () -> false);

        assertEquals(LouvainWorkerRunResult.Status.COMPLETED, result.status());
        assertEquals(parameters, result.parameters());
        assertEquals(0.5, result.resolution());
        assertEquals("undirected_mutual", result.detection().relationProjection());
        assertEquals(3, result.quality().inputDirectedRelationshipCount());
        assertEquals(1, result.quality().projectedUndirectedTieCount());
        assertEquals(1, result.quality().unassignedNodeCount());
    }

    @Test
    void cancellationTerminatesAnAlreadyRunningWorker() {
        WindowsLouvainAnalysisRunner runner =
                runner(
                        WindowsJobLimits.DEFAULT,
                        LouvainRunnerTestWorkers.Wait.class.getName(),
                        Duration.ofSeconds(3));
        AtomicInteger cancellationChecks = new AtomicInteger();

        LouvainWorkerRunResult result = runner.runForControlledVerification(
                nodes("A", "B", "C"),
                edges("A", "B"),
                () -> cancellationChecks.incrementAndGet() >= 3);

        assertEquals(LouvainWorkerRunResult.Status.CANCELLED, result.status());
        assertFalse(result.terminationDetail().isBlank());
    }

    @Test
    void endToEndDeadlineTerminatesAnAlreadyRunningWorker() {
        WindowsLouvainAnalysisRunner runner =
                runner(
                        WindowsJobLimits.DEFAULT,
                        LouvainRunnerTestWorkers.Wait.class.getName(),
                        Duration.ofMillis(700));

        LouvainWorkerRunResult result = runner.runForControlledVerification(
                nodes("A", "B", "C"),
                edges("A", "B"),
                () -> false);

        assertEquals(LouvainWorkerRunResult.Status.TIME_LIMIT, result.status());
        assertFalse(result.terminationDetail().isBlank());
    }

    @Test
    void mapsUnexpectedWorkerExitToFailure() {
        WindowsLouvainAnalysisRunner runner =
                runner(
                        WindowsJobLimits.DEFAULT,
                        LouvainRunnerTestWorkers.Exit.class.getName(),
                        Duration.ofSeconds(3));

        LouvainWorkerRunResult result = runner.runForControlledVerification(
                nodes("A", "B", "C"),
                edges("A", "B"),
                () -> false);

        assertEquals(LouvainWorkerRunResult.Status.FAILED, result.status());
        assertEquals("worker_exit_code_23", result.terminationDetail());
    }

    @Test
    void productionEntryRunsWithWorkerLimitsWithoutSystemMemoryPreflight() {
        WindowsLouvainAnalysisRunner runner = runner(
                WindowsJobLimits.DEFAULT, LouvainAnalysisWorkerMain.class.getName());

        LouvainWorkerRunResult result =
                runner.run(nodes("A", "B", "C"), edges("A", "B", "B", "C"), () -> false);

        assertEquals(LouvainWorkerRunResult.Status.COMPLETED, result.status());
    }

    private static WindowsLouvainAnalysisRunner runner(
            WindowsJobLimits limits, String workerClass) {
        return new WindowsLouvainAnalysisRunner(
                JAVA_EXECUTABLE, WORKER_CLASS_PATH, limits, workerClass);
    }

    private static WindowsLouvainAnalysisRunner runner(
            WindowsJobLimits limits, String workerClass, Duration timeLimit) {
        return new WindowsLouvainAnalysisRunner(
                JAVA_EXECUTABLE, WORKER_CLASS_PATH, limits, workerClass, timeLimit);
    }

    private static List<NetworkNode> nodes(String... ids) {
        return Arrays.stream(ids)
                .map(id -> new NetworkNode(
                        id, NetworkNodeType.PROFILE, 0, 0, 0.0, false))
                .toList();
    }

    private static List<NetworkEdge> edges(String... endpoints) {
        List<NetworkEdge> edges = new ArrayList<>();
        for (int index = 0; index < endpoints.length; index += 2) {
            edges.add(new NetworkEdge(
                    endpoints[index], endpoints[index + 1], "following"));
        }
        return List.copyOf(edges);
    }
}
