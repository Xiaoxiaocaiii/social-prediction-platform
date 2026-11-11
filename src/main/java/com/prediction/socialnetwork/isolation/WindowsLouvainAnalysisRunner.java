package com.prediction.socialnetwork.isolation;

import com.prediction.socialnetwork.application.LouvainAnalysisExecutor;
import com.prediction.socialnetwork.network.LouvainAnalysisWorkerMain;
import com.prediction.socialnetwork.network.LouvainAnalysisWorkerProtocol;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
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
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

import com.prediction.socialnetwork.isolation.WindowsJobProcess.Completion;
import com.prediction.socialnetwork.isolation.WindowsJobProcess.CompletionStatus;
import com.prediction.socialnetwork.isolation.LouvainWorkerRunResult.Status;

@Component
public final class WindowsLouvainAnalysisRunner implements LouvainAnalysisExecutor {

    private static final Duration MAX_TIME_LIMIT = Duration.ofSeconds(30);
    private static final long POLL_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(20);
    private static final long MEMORY_LIMIT_TOLERANCE_BYTES = WindowsJobLimits.MEBIBYTE;
    private static final String BOOT_JAR_LAUNCHER =
            "org.springframework.boot.loader.launch.PropertiesLauncher";
    private static final String BOOT_JAR_MAIN_CLASS =
            "org.springframework.boot.loader.launch.JarLauncher";

    private final Path javaExecutable;
    private final String workerClassPath;
    private final String workerMainClass;
    private final WindowsJobLimits jobLimits;
    private final long timeLimitNanos;

    public WindowsLouvainAnalysisRunner() {
        this(
                Path.of(System.getProperty("java.home"), "bin", "javaw.exe"),
                System.getProperty(
                        "surefire.test.class.path",
                        System.getProperty("java.class.path", "")),
                WindowsJobLimits.DEFAULT,
                LouvainAnalysisWorkerMain.class.getName(),
                MAX_TIME_LIMIT);
    }

    public WindowsLouvainAnalysisRunner(
            Path javaExecutable,
            String workerClassPath,
            WindowsJobLimits jobLimits) {
        this(
                javaExecutable,
                workerClassPath,
                jobLimits,
                LouvainAnalysisWorkerMain.class.getName(),
                MAX_TIME_LIMIT);
    }

    WindowsLouvainAnalysisRunner(
            Path javaExecutable,
            String workerClassPath,
            WindowsJobLimits jobLimits,
            String workerMainClass) {
        this(
                javaExecutable,
                workerClassPath,
                jobLimits,
                workerMainClass,
                MAX_TIME_LIMIT);
    }

    WindowsLouvainAnalysisRunner(
            Path javaExecutable,
            String workerClassPath,
            WindowsJobLimits jobLimits,
            String workerMainClass,
            Duration requestedTimeLimit) {
        this.javaExecutable = Objects.requireNonNull(javaExecutable, "javaExecutable");
        this.workerClassPath = Objects.requireNonNull(workerClassPath, "workerClassPath");
        this.jobLimits = Objects.requireNonNull(jobLimits, "jobLimits");
        this.workerMainClass = Objects.requireNonNull(workerMainClass, "workerMainClass");
        Objects.requireNonNull(requestedTimeLimit, "requestedTimeLimit");
        if (requestedTimeLimit.isZero() || requestedTimeLimit.isNegative()) {
            throw new IllegalArgumentException("time limit must be positive");
        }
        this.timeLimitNanos = Math.min(
                requestedTimeLimit.toNanos(), MAX_TIME_LIMIT.toNanos());
        if (!javaExecutable.isAbsolute()) {
            throw new IllegalArgumentException("Java executable path must be absolute");
        }
    }

    public LouvainWorkerRunResult run(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            BooleanSupplier cancellationRequested) {
        return run(nodes, directedEdges, LouvainAnalysisParameters.DEFAULT, cancellationRequested);
    }

    @Override
    public LouvainWorkerRunResult run(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            LouvainAnalysisParameters parameters,
            BooleanSupplier cancellationRequested) {
        return runBounded(nodes, directedEdges, parameters, cancellationRequested, false);
    }

    LouvainWorkerRunResult runForControlledVerification(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            BooleanSupplier cancellationRequested) {
        return runForControlledVerification(
                nodes,
                directedEdges,
                LouvainAnalysisParameters.DEFAULT,
                cancellationRequested);
    }

    LouvainWorkerRunResult runForControlledVerification(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            LouvainAnalysisParameters parameters,
            BooleanSupplier cancellationRequested) {
        return runBounded(nodes, directedEdges, parameters, cancellationRequested, true);
    }

    private LouvainWorkerRunResult runBounded(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            LouvainAnalysisParameters parameters,
            BooleanSupplier cancellationRequested,
            boolean controlledVerification) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(directedEdges, "directedEdges");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");

        long startedAt = System.nanoTime();
        AnalysisRunGate.Lease lease = AnalysisRunGate.shared().tryAcquire();
        if (lease == null) {
            return result(Status.BUSY, "analysis_already_running", startedAt);
        }

        Path temporaryDirectory = null;
        IOException cleanupFailure = null;
        LouvainWorkerRunResult runResult =
                result(Status.FAILED, "worker_result_not_available", startedAt);
        long requestBytes = 0;
        try {
            if (isCancelled(cancellationRequested)) {
                runResult = result(Status.CANCELLED, "cancelled_before_worker_start", startedAt);
            } else if (workerClassPath.isBlank() || !Files.isRegularFile(javaExecutable)) {
                runResult = result(
                        Status.FAILED, "worker_launch_configuration_invalid", startedAt);
            } else {
                long deadline = startedAt + timeLimitNanos;
                temporaryDirectory = Files.createTempDirectory("social-network-louvain-");
                Path inputPath = temporaryDirectory.resolve("request.json");
                Path outputPath = temporaryDirectory.resolve("result.json");

                try {
                    LouvainAnalysisWorkerProtocol.writeRequest(
                            inputPath,
                            LouvainAnalysisWorkerProtocol.requestFrom(
                                    nodes, directedEdges, parameters));
                    requestBytes = Files.size(inputPath);
                } catch (LouvainAnalysisWorkerProtocol.PayloadTooLargeException exception) {
                    runResult = result(Status.FAILED, "worker_input_size_limit", startedAt);
                }

                if (requestBytes > 0) {
                    if (isCancelled(cancellationRequested)) {
                        runResult = result(
                                Status.CANCELLED, "cancelled_before_worker_start", startedAt);
                    } else if (System.nanoTime() >= deadline) {
                        runResult = result(
                                Status.TIME_LIMIT,
                                "end_to_end_deadline_before_worker_start",
                                startedAt);
                    } else {
                        runResult = executeWorker(
                                inputPath,
                                outputPath,
                                nodes,
                                directedEdges,
                                parameters,
                                requestBytes,
                                deadline,
                                startedAt,
                                cancellationRequested,
                                controlledVerification);
                    }
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            runResult = result(Status.CANCELLED, "supervisor_interrupted", startedAt);
        } catch (RuntimeException exception) {
            runResult = classifyLaunchFailure(exception, startedAt);
        } catch (IOException exception) {
            runResult = result(Status.FAILED, "worker_io_failure", startedAt);
        } finally {
            try {
                cleanupFailure = deleteTemporaryDirectory(temporaryDirectory);
            } finally {
                lease.close();
            }
        }

        if (cleanupFailure != null) {
            return result(Status.FAILED, "worker_temporary_cleanup_failed", startedAt);
        }
        if (System.nanoTime() - startedAt >= timeLimitNanos
                && runResult.status() != Status.TIME_LIMIT
                && runResult.status() != Status.CANCELLED
                && runResult.status() != Status.MEMORY_LIMIT
                && runResult.status() != Status.BUSY
                && runResult.status() != Status.FAILED) {
            return result(Status.TIME_LIMIT, "end_to_end_deadline_exceeded", startedAt);
        }
        if (runResult.requestBytes() == 0 && requestBytes > 0) {
            return withRequestBytes(runResult, requestBytes);
        }
        return runResult;
    }

    private LouvainWorkerRunResult executeWorker(
            Path inputPath,
            Path outputPath,
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            LouvainAnalysisParameters parameters,
            long requestBytes,
            long deadline,
            long startedAt,
            BooleanSupplier cancellationRequested,
            boolean controlledVerification) throws InterruptedException, IOException {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            return result(
                    Status.TIME_LIMIT, "deadline_before_process_start", startedAt, requestBytes, 0);
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

        try (WindowsJobProcess process = controlledVerification
                ? WindowsJobProcess.startForControlledVerification(
                        javaExecutable, arguments, jobLimits)
                : WindowsJobProcess.start(javaExecutable, arguments, jobLimits)) {
            while (process.isRunning()) {
                if (isCancelled(cancellationRequested)) {
                    process.terminate();
                    return result(
                            Status.CANCELLED,
                            "cancelled_running_worker",
                            startedAt,
                            requestBytes,
                            0,
                            process.peakJobMemoryBytes(),
                            process.totalCpuTime100Nanoseconds());
                }
                if (System.nanoTime() >= deadline) {
                    process.terminate();
                    return result(
                            Status.TIME_LIMIT,
                            "end_to_end_deadline",
                            startedAt,
                            requestBytes,
                            0,
                            process.peakJobMemoryBytes(),
                            process.totalCpuTime100Nanoseconds());
                }
                long remaining = Math.max(1, deadline - System.nanoTime());
                TimeUnit.NANOSECONDS.sleep(Math.min(POLL_INTERVAL_NANOS, remaining));
            }

            Completion completion = process.await(Duration.ZERO);
            long peakMemoryBytes = process.peakJobMemoryBytes();
            long cpuTime = process.totalCpuTime100Nanoseconds();
            if (completion.status() == CompletionStatus.TIME_LIMIT) {
                return result(
                        Status.TIME_LIMIT,
                        "worker_wait_timeout",
                        startedAt,
                        requestBytes,
                        0,
                        peakMemoryBytes,
                        cpuTime);
            }
            if (completion.exitCode() != 0 || !Files.exists(outputPath)) {
                return workerExitFailure(
                        completion.exitCode(),
                        peakMemoryBytes,
                        startedAt,
                        requestBytes,
                        cpuTime);
            }

            try {
                LouvainAnalysisWorkerProtocol.WorkerResult workerResult =
                        LouvainAnalysisWorkerProtocol.readResult(
                                outputPath, nodes, directedEdges, parameters);
                long artifactBytes = Files.size(outputPath);
                if (System.nanoTime() >= deadline) {
                    return result(
                            Status.TIME_LIMIT,
                            "end_to_end_deadline_during_result_validation",
                            startedAt,
                            requestBytes,
                            artifactBytes,
                            peakMemoryBytes,
                            cpuTime);
                }
                if (workerResult.status()
                        == LouvainAnalysisWorkerProtocol.WorkerStatus.FAILED) {
                    return result(
                            Status.FAILED,
                            workerResult.terminationDetail(),
                            startedAt,
                            requestBytes,
                            artifactBytes,
                            peakMemoryBytes,
                            cpuTime);
                }
                return new LouvainWorkerRunResult(
                        Status.COMPLETED,
                        workerResult.algorithmVersion(),
                        workerResult.resolution(),
                        workerResult.parameters(),
                        workerResult.detection(),
                        workerResult.quality(),
                        null,
                        elapsedMillis(startedAt),
                        peakMemoryBytes,
                        cpuTime,
                        requestBytes,
                        artifactBytes);
            } catch (LouvainAnalysisWorkerProtocol.PayloadTooLargeException exception) {
                return result(
                        Status.FAILED,
                        "worker_result_size_limit",
                        startedAt,
                        requestBytes,
                        0,
                        peakMemoryBytes,
                        cpuTime);
            } catch (IOException | RuntimeException exception) {
                return result(
                        Status.FAILED,
                        "worker_result_protocol_invalid",
                        startedAt,
                        requestBytes,
                        0,
                        peakMemoryBytes,
                        cpuTime);
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

    private LouvainWorkerRunResult workerExitFailure(
            int exitCode,
            long peakMemoryBytes,
            long startedAt,
            long requestBytes,
            long cpuTime) {
        if (peakMemoryBytes >= Math.max(
                1,
                jobLimits.jobMemoryBytes() - MEMORY_LIMIT_TOLERANCE_BYTES)) {
            return result(
                    Status.MEMORY_LIMIT,
                    "worker_exit_near_job_memory_limit",
                    startedAt,
                    requestBytes,
                    0,
                    peakMemoryBytes,
                    cpuTime);
        }
        return result(
                Status.FAILED,
                "worker_exit_code_" + Integer.toUnsignedString(exitCode),
                startedAt,
                requestBytes,
                0,
                peakMemoryBytes,
                cpuTime);
    }

    private LouvainWorkerRunResult classifyLaunchFailure(
            RuntimeException exception, long startedAt) {
        return result(Status.FAILED, exception.getClass().getName(), startedAt);
    }

    private static boolean isCancelled(BooleanSupplier cancellationRequested) {
        return Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean();
    }

    private static LouvainWorkerRunResult result(
            Status status, String detail, long startedAt) {
        return result(status, detail, startedAt, 0, 0, 0, 0);
    }

    private static LouvainWorkerRunResult result(
            Status status,
            String detail,
            long startedAt,
            long requestBytes,
            long resultBytes) {
        return result(status, detail, startedAt, requestBytes, resultBytes, 0, 0);
    }

    private static LouvainWorkerRunResult result(
            Status status,
            String detail,
            long startedAt,
            long requestBytes,
            long resultBytes,
            long peakMemoryBytes,
            long cpuTime) {
        return new LouvainWorkerRunResult(
                status,
                null,
                0.0,
                null,
                null,
                null,
                detail,
                elapsedMillis(startedAt),
                peakMemoryBytes,
                cpuTime,
                requestBytes,
                resultBytes);
    }

    private static LouvainWorkerRunResult withRequestBytes(
            LouvainWorkerRunResult result, long requestBytes) {
        return new LouvainWorkerRunResult(
                result.status(),
                result.algorithmVersion(),
                result.resolution(),
                result.parameters(),
                result.detection(),
                result.quality(),
                result.terminationDetail(),
                result.elapsedMillis(),
                result.peakJobMemoryBytes(),
                result.totalCpuTime100Nanoseconds(),
                requestBytes,
                result.resultArtifactBytes());
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
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
