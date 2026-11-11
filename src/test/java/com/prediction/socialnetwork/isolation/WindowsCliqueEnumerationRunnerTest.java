package com.prediction.socialnetwork.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.network.CliqueEnumerationLimits;
import com.prediction.socialnetwork.network.CliqueEnumerationResult;
import com.prediction.socialnetwork.network.CliqueEnumerationStatus;
import com.prediction.socialnetwork.network.CliqueEnumerationWorkerMain;
import com.prediction.socialnetwork.network.CliqueEnumerationWorkerProtocol;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WindowsCliqueEnumerationRunnerTest {

    private static final Path JAVA_EXECUTABLE =
            Path.of(System.getProperty("java.home"), "bin", "javaw.exe");
    private static final String WORKER_CLASS_PATH = System.getProperty(
            "surefire.test.class.path",
            System.getProperty("java.class.path"));

    @BeforeAll
    static void requireWindowsAndSafetyMargin() {
        Assumptions.assumeTrue(
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows"),
                "Windows Job Object integration tests require Windows");
    }

    @Test
    void runsTheCliqueEnumeratorInsideTheBoundedWorkerAndReadsItsArtifact() {
        WindowsCliqueEnumerationRunner runner = runner(
                WindowsJobLimits.DEFAULT, CliqueEnumerationWorkerMain.class.getName());

        CliqueEnumerationResult result = runner.run(
                nodes("A", "B", "C"),
                edges("A", "B", "A", "C", "B", "C"),
                () -> false);

        assertEquals(CliqueEnumerationStatus.COMPLETED, result.status());
        assertTrue(result.isComplete());
        assertEquals(List.of(List.of("A", "B", "C")), result.cliques());
        assertEquals(1, result.enumeratedCliqueCount());
        assertEquals(java.util.Map.of(3, 1), result.cliqueSizeDistribution());
        assertEquals(
                java.util.Map.of("A", 1, "B", 1, "C", 1),
                result.observedParticipationCounts());
        assertTrue(result.membershipListComplete());
        assertEquals(
                CliqueEnumerationWorkerProtocol.MAX_CLIQUE_ARRAY_BYTES,
                result.limits().maxCliqueArrayBytes());
    }

    @Test
    void runsTheIndependentBitSetImplementationInsideTheBoundedWorker() {
        WindowsCliqueEnumerationRunner runner = runner(
                WindowsJobLimits.DEFAULT,
                BitSetCliqueCrossCheckWorkerMain.class.getName());

        CliqueEnumerationResult result = runner.runForControlledVerification(
                nodes("A", "B", "C", "D"),
                edges("A", "B", "A", "C", "B", "C", "B", "D", "C", "D"),
                CliqueEnumerationLimits.DEFAULT,
                () -> false);

        assertEquals(CliqueEnumerationStatus.COMPLETED, result.status());
        assertTrue(result.isComplete());
        assertEquals(
                List.of(List.of("A", "B", "C"), List.of("B", "C", "D")),
                result.cliques());
        assertEquals(2, result.enumeratedCliqueCount());
        assertTrue(result.membershipListComplete());
    }

    @Test
    void launchesTheWorkerFromThePackagedSpringBootArchive() {
        String archiveValue = System.getProperty("clique.worker.archive");
        Assumptions.assumeTrue(
                archiveValue != null && Files.isRegularFile(Path.of(archiveValue)),
                "Set clique.worker.archive to the repackaged Spring Boot JAR");
        WindowsCliqueEnumerationRunner runner = new WindowsCliqueEnumerationRunner(
                JAVA_EXECUTABLE,
                Path.of(archiveValue).toAbsolutePath().normalize().toString(),
                WindowsJobLimits.DEFAULT,
                CliqueEnumerationWorkerMain.class.getName());

        CliqueEnumerationResult result = runner.runForControlledVerification(
                nodes("A", "B", "C"),
                edges("A", "B", "A", "C", "B", "C"),
                CliqueEnumerationLimits.DEFAULT,
                () -> false);

        assertEquals(CliqueEnumerationStatus.COMPLETED, result.status());
        assertTrue(result.isComplete());
        assertEquals(List.of(List.of("A", "B", "C")), result.cliques());
    }

    @Test
    void cancellationTerminatesAnAlreadyRunningCliqueWorker() {
        WindowsCliqueEnumerationRunner runner = runner(
                WindowsJobLimits.DEFAULT, CliqueRunnerWaitWorker.class.getName());
        AtomicInteger cancellationChecks = new AtomicInteger();

        CliqueEnumerationResult result = runner.runForControlledVerification(
                nodes("A", "B", "C"),
                edges("A", "B", "A", "C", "B", "C"),
                CliqueEnumerationLimits.DEFAULT,
                () -> cancellationChecks.incrementAndGet() >= 3);

        assertEquals(CliqueEnumerationStatus.CANCELLED, result.status());
        assertFalse(result.isComplete());
    }

    @Test
    void endToEndDeadlineTerminatesAnAlreadyRunningCliqueWorker() {
        WindowsCliqueEnumerationRunner runner = runner(
                WindowsJobLimits.DEFAULT, CliqueRunnerWaitWorker.class.getName());
        CliqueEnumerationLimits shortDeadline =
                new CliqueEnumerationLimits(Duration.ofMillis(700), 10, 1024);

        CliqueEnumerationResult result = runner.runForControlledVerification(
                nodes("A", "B", "C"),
                edges("A", "B", "A", "C", "B", "C"),
                shortDeadline,
                () -> false);

        assertEquals(CliqueEnumerationStatus.TIME_LIMIT, result.status());
        assertFalse(result.isComplete());
    }

    @Test
    void mapsAnUnexpectedWorkerExitToAnIncompleteFailure() {
        WindowsCliqueEnumerationRunner runner = runner(
                WindowsJobLimits.DEFAULT, CliqueRunnerExitWorker.class.getName());

        CliqueEnumerationResult result = runner.runForControlledVerification(
                nodes("A", "B", "C"),
                edges("A", "B", "A", "C", "B", "C"),
                CliqueEnumerationLimits.DEFAULT,
                () -> false);

        assertEquals(CliqueEnumerationStatus.FAILED, result.status());
        assertFalse(result.isComplete());
        assertEquals("worker_exit_code_23", result.terminationDetail());
    }

    @Test
    void mapsAJobMemoryLimitExitToAnIncompleteMemoryStatus() {
        long memoryLimit = 64 * WindowsJobLimits.MEBIBYTE;
        WindowsJobLimits smallJob = new WindowsJobLimits(memoryLimit, memoryLimit, 2500, 1);
        WindowsCliqueEnumerationRunner runner =
                runner(smallJob, CliqueRunnerMemoryWorker.class.getName());

        CliqueEnumerationResult result = runner.runForControlledVerification(
                nodes("A", "B", "C"),
                edges("A", "B", "A", "C", "B", "C"),
                CliqueEnumerationLimits.DEFAULT,
                () -> false);

        assertEquals(CliqueEnumerationStatus.MEMORY_LIMIT, result.status());
        assertFalse(result.isComplete());
        assertEquals(0, result.retainedCliqueCount());
    }

    private static WindowsCliqueEnumerationRunner runner(
            WindowsJobLimits limits, String workerClass) {
        return new WindowsCliqueEnumerationRunner(
                JAVA_EXECUTABLE, WORKER_CLASS_PATH, limits, workerClass);
    }

    private static List<NetworkNode> nodes(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> new NetworkNode(id, NetworkNodeType.PROFILE, 0, 0, 0.0, false))
                .toList();
    }

    private static List<NetworkEdge> edges(String... endpoints) {
        java.util.ArrayList<NetworkEdge> edges = new java.util.ArrayList<>();
        for (int index = 0; index < endpoints.length; index += 2) {
            edges.add(new NetworkEdge(endpoints[index], endpoints[index + 1], "following"));
        }
        return List.copyOf(edges);
    }
}
