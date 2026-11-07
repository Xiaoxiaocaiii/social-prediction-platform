package com.prediction.socialnetwork.isolation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WindowsJobTestWorker {

    private static final long MAX_TEST_ALLOCATION_BYTES = 384L * WindowsJobLimits.MEBIBYTE;
    private static final long ALLOCATION_CHUNK_BYTES = 8L * WindowsJobLimits.MEBIBYTE;

    private WindowsJobTestWorker() {
    }

    public static void main(String[] arguments) throws Exception {
        switch (arguments[0]) {
            case "sleep" -> sleep(Path.of(arguments[1]), Long.parseLong(arguments[2]));
            case "allocate" -> allocate(Path.of(arguments[1]));
            case "try-child" -> tryChild(Path.of(arguments[1]));
            case "spawn-tree" -> spawnTree(
                    Path.of(arguments[1]), Path.of(arguments[2]), Path.of(arguments[3]));
            case "cpu" -> burnCpu(Path.of(arguments[1]), Long.parseLong(arguments[2]));
            default -> throw new IllegalArgumentException("Unknown test worker mode");
        }
    }

    private static void sleep(Path readyFile, long durationMillis) throws Exception {
        write(readyFile, Long.toString(ProcessHandle.current().pid()));
        Thread.sleep(durationMillis);
    }

    private static void allocate(Path statusFile) throws IOException {
        write(statusFile, "READY");
        List<byte[]> committedChunks = new ArrayList<>();
        long allocatedBytes = 0;
        try {
            while (allocatedBytes < MAX_TEST_ALLOCATION_BYTES) {
                byte[] chunk = new byte[(int) ALLOCATION_CHUNK_BYTES];
                for (int offset = 0; offset < chunk.length; offset += 4096) {
                    chunk[offset] = 1;
                }
                committedChunks.add(chunk);
                allocatedBytes += chunk.length;
            }
        } catch (OutOfMemoryError expectedUnderJobLimit) {
            committedChunks.clear();
            System.gc();
            write(statusFile, "LIMIT_REACHED");
            System.exit(42);
            return;
        }
        write(statusFile, "UNRESTRICTED_ALLOCATION_COMPLETED");
    }

    private static void tryChild(Path statusFile) throws Exception {
        List<String> command = javaCommand(
                System.getProperty("java.class.path"),
                "sleep",
                statusFile.resolveSibling("unexpected-child.ready").toString(),
                "60000");
        try {
            Process child = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            Thread.sleep(300);
            write(statusFile, child.isAlive() ? "CHILD_ALLOWED" : "CHILD_REJECTED");
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor();
            }
        } catch (IOException rejectedByJobLimit) {
            write(statusFile, "CHILD_REJECTED");
        }
    }

    private static void spawnTree(Path parentReady, Path childPidFile, Path childReady) throws Exception {
        List<String> command = javaCommand(
                System.getProperty("java.class.path"),
                "sleep",
                childReady.toString(),
                "60000");
        Process child = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        write(childPidFile, Long.toString(child.pid()));
        write(parentReady, Long.toString(ProcessHandle.current().pid()));
        Thread.sleep(Duration.ofMinutes(2).toMillis());
    }

    private static void burnCpu(Path readyFile, long durationMillis) throws Exception {
        int threadCount = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch started = new CountDownLatch(threadCount);
        List<Thread> burners = new ArrayList<>();
        for (int index = 0; index < threadCount; index++) {
            Thread burner = new Thread(() -> {
                started.countDown();
                long value = 1;
                while (running.get()) {
                    value = (value * 31 + 7) & 0x7fffffff;
                }
                if (value == Long.MIN_VALUE) {
                    throw new AssertionError("unreachable");
                }
            }, "cl03-cpu-worker-" + index);
            burner.setDaemon(true);
            burners.add(burner);
            burner.start();
        }
        started.await();
        write(readyFile, Long.toString(ProcessHandle.current().pid()));
        Thread.sleep(durationMillis);
        running.set(false);
        for (Thread burner : burners) {
            burner.join(2_000);
        }
    }

    private static List<String> javaCommand(String classPath, String mode, String... modeArguments) {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "javaw.exe").toString());
        command.add("-Xms8m");
        command.add("-Xmx512m");
        command.add("-Xss512k");
        command.add("-cp");
        command.add(classPath);
        command.add(WindowsJobTestWorker.class.getName());
        command.add(mode);
        command.addAll(List.of(modeArguments));
        return command;
    }

    private static void write(Path path, String value) throws IOException {
        Files.writeString(path, value);
    }
}
