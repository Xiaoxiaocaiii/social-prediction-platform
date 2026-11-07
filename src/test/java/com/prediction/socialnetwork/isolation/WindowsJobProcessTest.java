package com.prediction.socialnetwork.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.isolation.WindowsJobProcess.Completion;
import com.prediction.socialnetwork.isolation.WindowsJobProcess.CompletionStatus;
import com.prediction.socialnetwork.isolation.WindowsJobProcess.LimitSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WindowsJobProcessTest {

    private Path temporaryDirectory;

    @BeforeAll
    static void requireWindowsAndSafetyMargin() {
        Assumptions.assumeTrue(
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows"),
                "Windows Job Object integration tests require Windows");
        long available = WindowsResourcePreflight.currentAvailablePhysicalMemoryBytes();
        Assumptions.assumeTrue(
                available >= 1536L * WindowsJobLimits.MEBIBYTE,
                "Skipped to preserve the 1.5 GiB margin for bounded synthetic workers");
    }

    @BeforeEach
    void createTemporaryDirectory() throws IOException {
        long available = WindowsResourcePreflight.currentAvailablePhysicalMemoryBytes();
        Assumptions.assumeTrue(
                available >= 1536L * WindowsJobLimits.MEBIBYTE,
                "Skipped to preserve the 1.5 GiB margin for bounded synthetic workers");
        temporaryDirectory = Files.createTempDirectory("social-network-cl03-");
    }

    @AfterEach
    void removeTemporaryDirectory() throws IOException {
        if (temporaryDirectory != null && Files.exists(temporaryDirectory)) {
            try (var paths = Files.walk(temporaryDirectory)) {
                for (Path path : paths.sorted((left, right) ->
                        right.getNameCount() - left.getNameCount()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void appliesAndReadsBackMemoryCpuAndActiveProcessLimits() throws Exception {
        Path readyFile = temporaryDirectory.resolve("ready");
        try (WindowsJobProcess process = startWorker(
                WindowsJobLimits.DEFAULT, "sleep", readyFile.toString(), "60000")) {
            awaitFile(readyFile, Duration.ofSeconds(5));
            LimitSnapshot limits = process.limitSnapshot();
            assertEquals(256 * WindowsJobLimits.MEBIBYTE, limits.processMemoryBytes());
            assertEquals(256 * WindowsJobLimits.MEBIBYTE, limits.jobMemoryBytes());
            assertEquals(1, limits.maxActiveProcesses());
            assertEquals(5, limits.cpuControlFlags() & 5);
            assertEquals(2500, limits.cpuRatePer10k());
            List<Long> processIds = process.activeProcessIds();
            assertEquals(
                    1,
                    process.activeProcessCount(),
                    () -> "Unexpected Job process ids: " + processIds.stream()
                            .map(processId -> processId + ":"
                                    + ProcessHandle.of(processId)
                                            .flatMap(handle -> handle.info().command())
                                            .orElse("exited"))
                            .toList());
        }
    }

    @Test
    void jobMemoryLimitStopsWorkerBeforeItsBoundedAllocationCompletes() throws Exception {
        Path statusFile = temporaryDirectory.resolve("allocation-status");
        try (WindowsJobProcess process =
                startWorker(WindowsJobLimits.DEFAULT, "allocate", statusFile.toString())) {
            awaitFile(statusFile, Duration.ofSeconds(5));
            Completion completion = process.await(Duration.ofSeconds(10));
            String status = Files.readString(statusFile);
            assertEquals(CompletionStatus.EXITED, completion.status());
            assertTrue(
                    "LIMIT_REACHED".equals(status) || completion.exitCode() != 0,
                    () -> "Worker neither reported a memory limit nor failed: " + status);
            assertFalse("UNRESTRICTED_ALLOCATION_COMPLETED".equals(status));
            assertTrue(process.peakJobMemoryBytes() > 64 * WindowsJobLimits.MEBIBYTE);
            assertTrue(process.peakJobMemoryBytes() <= 256 * WindowsJobLimits.MEBIBYTE);
            assertEquals(0, process.activeProcessCount());
        }
    }

    @Test
    void cpuHardCapKeepsSyntheticBurnBelowConfiguredSystemShare() throws Exception {
        Path readyFile = temporaryDirectory.resolve("cpu-ready");
        long startedAt = System.nanoTime();
        try (WindowsJobProcess process =
                startWorker(WindowsJobLimits.DEFAULT, "cpu", readyFile.toString(), "2500")) {
            awaitFile(readyFile, Duration.ofSeconds(5));
            Completion completion = process.await(Duration.ofSeconds(10));
            long elapsedNanos = System.nanoTime() - startedAt;
            assertEquals(CompletionStatus.EXITED, completion.status());

            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
            double cpuSeconds = process.totalCpuTime100Nanoseconds() / 10_000_000.0;
            double maximumExpectedCpuSeconds =
                    elapsedSeconds * Runtime.getRuntime().availableProcessors() * 0.25 + 1.5;
            assertTrue(
                    cpuSeconds <= maximumExpectedCpuSeconds,
                    () -> "Job CPU time exceeded its configured hard cap: "
                            + cpuSeconds + " > " + maximumExpectedCpuSeconds);
        }
    }

    @Test
    void timeoutTerminatesTheAssignedWorker() throws Exception {
        Path readyFile = temporaryDirectory.resolve("timeout-ready");
        try (WindowsJobProcess process =
                startWorker(WindowsJobLimits.DEFAULT, "sleep", readyFile.toString(), "60000")) {
            awaitFile(readyFile, Duration.ofSeconds(5));
            Completion completion = process.await(Duration.ofMillis(500));
            assertEquals(CompletionStatus.TIME_LIMIT, completion.status());
            assertEquals(0, process.activeProcessCount());
        }
    }

    @Test
    void activeProcessLimitRejectsAWorkerChild() throws Exception {
        Path statusFile = temporaryDirectory.resolve("child-status");
        try (WindowsJobProcess process =
                startWorker(WindowsJobLimits.DEFAULT, "try-child", statusFile.toString())) {
            awaitFile(statusFile, Duration.ofSeconds(5));
            Completion completion = process.await(Duration.ofSeconds(5));
            assertEquals(CompletionStatus.EXITED, completion.status());
            assertEquals("CHILD_REJECTED", Files.readString(statusFile));
            assertEquals(0, process.activeProcessCount());
        }
    }

    @Test
    void cancellationTerminatesTheWholeJobTree() throws Exception {
        Path parentReady = temporaryDirectory.resolve("parent-ready");
        Path childPidFile = temporaryDirectory.resolve("child-pid");
        Path childReady = temporaryDirectory.resolve("child-ready");
        WindowsJobLimits treeLimits = new WindowsJobLimits(
                256 * WindowsJobLimits.MEBIBYTE,
                256 * WindowsJobLimits.MEBIBYTE,
                2500,
                4);
        try (WindowsJobProcess process =
                startWorker(treeLimits, "spawn-tree", parentReady.toString(),
                        childPidFile.toString(), childReady.toString())) {
            awaitFile(childReady, Duration.ofSeconds(5));
            long parentPid = Long.parseLong(Files.readString(parentReady));
            long childPid = Long.parseLong(Files.readString(childPidFile));
            Set<Long> jobProcessIds = new HashSet<>(process.activeProcessIds());
            assertTrue(jobProcessIds.contains(parentPid));
            assertTrue(jobProcessIds.contains(childPid));
            assertEquals(jobProcessIds.size(), process.activeProcessCount());
            assertTrue(process.activeProcessCount() <= treeLimits.maxActiveProcesses());

            process.terminate();

            assertEquals(0, process.activeProcessCount());
            assertProcessExited(parentPid);
            assertProcessExited(childPid);
        }
    }

    @Test
    void closingJobHandleKillsItsRunningWorker() throws Exception {
        Path readyFile = temporaryDirectory.resolve("close-ready");
        WindowsJobProcess process =
                startWorker(WindowsJobLimits.DEFAULT, "sleep", readyFile.toString(), "60000");
        awaitFile(readyFile, Duration.ofSeconds(5));
        long processId = Long.parseLong(Files.readString(readyFile));

        process.close();

        assertProcessExited(processId);
    }

    @Test
    void preflightRequiresAtLeastTwoGibibytesOfAvailableMemory() {
        assertFalse(WindowsResourcePreflight.hasRequiredMemory(
                2 * WindowsJobLimits.GIBIBYTE - 1));
        assertTrue(WindowsResourcePreflight.hasRequiredMemory(
                2 * WindowsJobLimits.GIBIBYTE));
    }

    private WindowsJobProcess startWorker(
            WindowsJobLimits limits, String mode, String... arguments) throws IOException {
        List<String> command = new java.util.ArrayList<>();
        command.add("-Xms8m");
        command.add("-Xmx512m");
        command.add("-Xss512k");
        command.add("-cp");
        command.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        command.add(WindowsJobTestWorker.class.getName());
        command.add(mode);
        command.addAll(List.of(arguments));
        return WindowsJobProcess.startForControlledVerification(
                Path.of(System.getProperty("java.home"), "bin", "javaw.exe"),
                command,
                limits);
    }

    private static void awaitFile(Path path, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!Files.exists(path)) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for test marker " + path.getFileName());
            }
            Thread.sleep(25);
        }
    }

    private static void assertProcessExited(long processId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false)) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Process remained alive after Job termination: " + processId);
            }
            Thread.sleep(25);
        }
    }
}
