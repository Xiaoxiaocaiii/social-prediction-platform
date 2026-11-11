package com.prediction.socialnetwork.isolation;

import com.prediction.socialnetwork.application.CliqueEnumerationExecutor;
import com.prediction.socialnetwork.network.CliqueEnumerationLimits;
import com.prediction.socialnetwork.network.CliqueEnumerationResult;
import com.prediction.socialnetwork.network.CliqueEnumerationStatus;
import com.prediction.socialnetwork.network.CliqueEnumerationWorkerMain;
import com.prediction.socialnetwork.network.CliqueEnumerationWorkerProtocol;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.springframework.stereotype.Component;

import com.prediction.socialnetwork.isolation.WindowsJobProcess.Completion;
import com.prediction.socialnetwork.isolation.WindowsJobProcess.CompletionStatus;

@Component
public final class WindowsCliqueEnumerationRunner implements CliqueEnumerationExecutor {

    private static final AnalysisRunGate PROCESS_RUN_GATE = AnalysisRunGate.shared();
    private static final long POLL_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(20);
    private static final long MEMORY_LIMIT_TOLERANCE_BYTES = WindowsJobLimits.MEBIBYTE;
    public static final CliqueEnumerationLimits DEFAULT_WORKER_LIMITS =
            CliqueEnumerationWorkerProtocol.DEFAULT_WORKER_LIMITS;
    private static final String BOOT_JAR_LAUNCHER =
            "org.springframework.boot.loader.launch.PropertiesLauncher";
    private static final String BOOT_JAR_MAIN_CLASS =
            "org.springframework.boot.loader.launch.JarLauncher";

    private final Path javaExecutable;
    private final String workerClassPath;
    private final String workerMainClass;
    private final WindowsJobLimits jobLimits;

    public WindowsCliqueEnumerationRunner() {
        this(
                Path.of(System.getProperty("java.home"), "bin", "javaw.exe"),
                System.getProperty(
                        "surefire.test.class.path",
                        System.getProperty("java.class.path", "")),
                WindowsJobLimits.DEFAULT,
                CliqueEnumerationWorkerMain.class.getName());
    }

    public WindowsCliqueEnumerationRunner(
            Path javaExecutable,
            String workerClassPath,
            WindowsJobLimits jobLimits) {
        this(
                javaExecutable,
                workerClassPath,
                jobLimits,
                CliqueEnumerationWorkerMain.class.getName());
    }

    WindowsCliqueEnumerationRunner(
            Path javaExecutable,
            String workerClassPath,
            WindowsJobLimits jobLimits,
            String workerMainClass) {
        this.javaExecutable = Objects.requireNonNull(javaExecutable, "javaExecutable");
        this.workerClassPath = Objects.requireNonNull(workerClassPath, "workerClassPath");
        this.jobLimits = Objects.requireNonNull(jobLimits, "jobLimits");
        this.workerMainClass = Objects.requireNonNull(workerMainClass, "workerMainClass");
        if (!javaExecutable.isAbsolute()) {
            throw new IllegalArgumentException("Java executable path must be absolute");
        }
    }

    @Override
    public CliqueEnumerationResult run(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            BooleanSupplier cancellationRequested) {
        return runBounded(
                nodes,
                directedEdges,
                DEFAULT_WORKER_LIMITS,
                cancellationRequested,
                false);
    }

    CliqueEnumerationResult runForControlledVerification(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            CliqueEnumerationLimits requestedLimits,
            BooleanSupplier cancellationRequested) {
        return runBounded(
                nodes, directedEdges, requestedLimits, cancellationRequested, true);
    }

    private CliqueEnumerationResult runBounded(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            CliqueEnumerationLimits requestedLimits,
            BooleanSupplier cancellationRequested,
            boolean controlledVerification) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(directedEdges, "directedEdges");
        Objects.requireNonNull(requestedLimits, "requestedLimits");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");

        CliqueEnumerationLimits boundedLimits = workerLimits(requestedLimits);
        long startedAt = System.nanoTime();
        AnalysisRunGate.Lease lease = PROCESS_RUN_GATE.tryAcquire();
        if (lease == null) {
            return CliqueEnumerationResult.externalTermination(
                    CliqueEnumerationStatus.BUSY, boundedLimits, "analysis_already_running");
        }

        Path temporaryDirectory = null;
        IOException cleanupFailure = null;
        CliqueEnumerationResult result = failedResult(boundedLimits, "worker_result_not_available");
        try {
            if (isCancelled(cancellationRequested)) {
                result = externalResult(
                        CliqueEnumerationStatus.CANCELLED,
                        boundedLimits,
                        "cancelled_before_worker_start");
            } else if (workerClassPath.isBlank() || !Files.isRegularFile(javaExecutable)) {
                result = failedResult(boundedLimits, "worker_launch_configuration_invalid");
            } else {
                long deadline = startedAt + boundedLimits.timeLimit().toNanos();
                temporaryDirectory = Files.createTempDirectory("social-network-clique-");
                Path inputPath = temporaryDirectory.resolve("request.json");
                Path outputPath = temporaryDirectory.resolve("result.json");

                boolean requestWritten = false;
                try {
                    CliqueEnumerationWorkerProtocol.writeRequest(
                            inputPath,
                            CliqueEnumerationWorkerProtocol.requestFrom(nodes, directedEdges));
                    requestWritten = true;
                } catch (CliqueEnumerationWorkerProtocol.PayloadTooLargeException exception) {
                    result = failedResult(boundedLimits, "worker_input_size_limit");
                }

                if (requestWritten) {
                    if (isCancelled(cancellationRequested)) {
                        result = externalResult(
                                CliqueEnumerationStatus.CANCELLED,
                                boundedLimits,
                                "cancelled_before_worker_start");
                    } else if (System.nanoTime() >= deadline) {
                        result = externalResult(
                                CliqueEnumerationStatus.TIME_LIMIT,
                                boundedLimits,
                                "end_to_end_deadline_before_worker_start");
                    } else {
                        result = executeWorker(
                                inputPath,
                                outputPath,
                                boundedLimits,
                                deadline,
                                cancellationRequested,
                                controlledVerification);
                    }
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            result = externalResult(
                    CliqueEnumerationStatus.CANCELLED,
                    boundedLimits,
                    "supervisor_interrupted");
        } catch (RuntimeException exception) {
            result = classifyLaunchFailure(exception, boundedLimits);
        } catch (IOException exception) {
            result = failedResult(boundedLimits, "worker_io_failure");
        } finally {
            try {
                cleanupFailure = deleteTemporaryDirectory(temporaryDirectory);
            } finally {
                lease.close();
            }
        }

        if (cleanupFailure != null) {
            return failedResult(boundedLimits, "worker_temporary_cleanup_failed");
        }
        if (System.nanoTime() - startedAt >= boundedLimits.timeLimit().toNanos()
                && result.status() != CliqueEnumerationStatus.TIME_LIMIT
                && result.status() != CliqueEnumerationStatus.CANCELLED
                && result.status() != CliqueEnumerationStatus.MEMORY_LIMIT
                && result.status() != CliqueEnumerationStatus.BUSY
                && result.status() != CliqueEnumerationStatus.FAILED) {
            return new CliqueEnumerationResult(
                    result.cliques(),
                    CliqueEnumerationStatus.TIME_LIMIT,
                    boundedLimits,
                    result.encodedCliqueArrayBytes(),
                    "end_to_end_deadline_exceeded",
                    result.enumeratedCliqueCount(),
                    result.cliqueSizeDistribution(),
                    result.nodeParticipationCounts(),
                    result.membershipListComplete());
        }
        return result;
    }

    private CliqueEnumerationResult executeWorker(
            Path inputPath,
            Path outputPath,
            CliqueEnumerationLimits limits,
            long deadline,
            BooleanSupplier cancellationRequested,
            boolean controlledVerification) throws InterruptedException, IOException {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            return externalResult(
                    CliqueEnumerationStatus.TIME_LIMIT, limits, "deadline_before_process_start");
        }

        List<String> arguments = new ArrayList<>();
        arguments.add("-Xms8m");
        arguments.add("-Xmx96m");
        arguments.add("-Xss512k");
        if (isSpringBootExecutableArchive()) {
            arguments.add("-Dloader.main=" + workerMainClass);
            arguments.add("-cp");
            arguments.add(workerClassPathArgument());
            arguments.add(BOOT_JAR_LAUNCHER);
        } else {
            arguments.add("-cp");
            arguments.add(workerClassPath);
            arguments.add(workerMainClass);
        }
        arguments.add(inputPath.toString());
        arguments.add(outputPath.toString());
        arguments.add(Long.toString(remainingNanos));
        arguments.add(Integer.toString(limits.maxCliques()));
        arguments.add(Long.toString(limits.maxCliqueArrayBytes()));

        CliqueEnumerationLimits workerExecutionLimits = new CliqueEnumerationLimits(
                Duration.ofNanos(remainingNanos),
                limits.maxCliques(),
                limits.maxCliqueArrayBytes());
        try (WindowsJobProcess process = controlledVerification
                ? WindowsJobProcess.startForControlledVerification(
                        javaExecutable, arguments, jobLimits)
                : WindowsJobProcess.start(javaExecutable, arguments, jobLimits)) {
            while (process.isRunning()) {
                if (Thread.currentThread().isInterrupted()
                        || cancellationRequested.getAsBoolean()) {
                    process.terminate();
                    return externalResult(
                            CliqueEnumerationStatus.CANCELLED, limits, "cancelled_running_worker");
                }
                if (System.nanoTime() >= deadline) {
                    process.terminate();
                    return externalResult(
                            CliqueEnumerationStatus.TIME_LIMIT, limits, "end_to_end_deadline");
                }
                long remaining = Math.max(1, deadline - System.nanoTime());
                TimeUnit.NANOSECONDS.sleep(Math.min(POLL_INTERVAL_NANOS, remaining));
            }

            Completion completion = process.await(Duration.ZERO);
            long peakMemoryBytes = process.peakJobMemoryBytes();
            if (completion.status() == CompletionStatus.TIME_LIMIT) {
                return externalResult(
                        CliqueEnumerationStatus.TIME_LIMIT, limits, "worker_wait_timeout");
            }
            if (completion.exitCode() != 0) {
                return workerExitFailure(completion.exitCode(), peakMemoryBytes, limits);
            }
            if (!Files.exists(outputPath)) {
                return workerExitFailure(completion.exitCode(), peakMemoryBytes, limits);
            }

            try {
                CliqueEnumerationResult result =
                        CliqueEnumerationWorkerProtocol.readResult(
                                outputPath, workerExecutionLimits);
                if (System.nanoTime() >= deadline) {
                    return new CliqueEnumerationResult(
                            result.cliques(),
                            CliqueEnumerationStatus.TIME_LIMIT,
                            workerExecutionLimits,
                            result.encodedCliqueArrayBytes(),
                            "end_to_end_deadline_during_result_validation",
                            result.enumeratedCliqueCount(),
                            result.cliqueSizeDistribution(),
                            result.nodeParticipationCounts(),
                            result.membershipListComplete());
                }
                return result;
            } catch (CliqueEnumerationWorkerProtocol.PayloadTooLargeException exception) {
                return externalResult(
                        CliqueEnumerationStatus.RESULT_SIZE_LIMIT,
                        workerExecutionLimits,
                        "worker_result_file_over_2_mib");
            } catch (IOException | RuntimeException exception) {
                return failedResult(workerExecutionLimits, "worker_result_protocol_invalid");
            }
        }
    }

    private boolean isSpringBootExecutableArchive() {
        if (workerClassPath.indexOf(File.pathSeparatorChar) >= 0) {
            return false;
        }
        Path archive = Path.of(workerClassPath);
        if (!Files.isRegularFile(archive)) {
            return false;
        }
        try (JarFile jar = new JarFile(archive.toFile())) {
            Manifest manifest = jar.getManifest();
            Attributes attributes = manifest == null ? null : manifest.getMainAttributes();
            String workerEntry = "BOOT-INF/classes/"
                    + workerMainClass.replace('.', '/') + ".class";
            return attributes != null
                    && BOOT_JAR_MAIN_CLASS.equals(
                            attributes.getValue(Attributes.Name.MAIN_CLASS))
                    && jar.getEntry(BOOT_JAR_LAUNCHER.replace('.', '/') + ".class") != null
                    && jar.getEntry(workerEntry) != null;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private String workerClassPathArgument() {
        return Path.of(workerClassPath).toAbsolutePath().normalize().toString();
    }

    private CliqueEnumerationLimits workerLimits(CliqueEnumerationLimits requested) {
        Duration timeLimit = requested.timeLimit().compareTo(
                CliqueEnumerationLimits.DEFAULT.timeLimit()) > 0
                ? CliqueEnumerationLimits.DEFAULT.timeLimit()
                : requested.timeLimit();
        return new CliqueEnumerationLimits(
                timeLimit,
                Math.min(requested.maxCliques(), CliqueEnumerationLimits.DEFAULT.maxCliques()),
                Math.min(
                        requested.maxCliqueArrayBytes(),
                        CliqueEnumerationWorkerProtocol.MAX_CLIQUE_ARRAY_BYTES));
    }

    private CliqueEnumerationResult workerExitFailure(
            int exitCode, long peakMemoryBytes, CliqueEnumerationLimits limits) {
        if (peakMemoryBytes >= Math.max(
                1,
                jobLimits.jobMemoryBytes() - MEMORY_LIMIT_TOLERANCE_BYTES)) {
            return externalResult(
                    CliqueEnumerationStatus.MEMORY_LIMIT,
                    limits,
                    "worker_exit_near_job_memory_limit");
        }
        return failedResult(limits, "worker_exit_code_" + Integer.toUnsignedString(exitCode));
    }

    private CliqueEnumerationResult classifyLaunchFailure(
            RuntimeException exception, CliqueEnumerationLimits limits) {
        return failedResult(limits, exception.getClass().getName());
    }

    private static boolean isCancelled(BooleanSupplier cancellationRequested) {
        return Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean();
    }

    private static CliqueEnumerationResult externalResult(
            CliqueEnumerationStatus status,
            CliqueEnumerationLimits limits,
            String detail) {
        return CliqueEnumerationResult.externalTermination(status, limits, detail);
    }

    private static CliqueEnumerationResult failedResult(
            CliqueEnumerationLimits limits, String detail) {
        return externalResult(CliqueEnumerationStatus.FAILED, limits, detail);
    }

    private static IOException deleteTemporaryDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return null;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
            return null;
        } catch (IOException exception) {
            return exception;
        }
    }
}
