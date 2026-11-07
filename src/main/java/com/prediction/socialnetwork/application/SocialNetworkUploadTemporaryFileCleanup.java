package com.prediction.socialnetwork.application;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SocialNetworkUploadTemporaryFileCleanup {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SocialNetworkUploadTemporaryFileCleanup.class);
    private static final Duration ORPHAN_RETENTION = Duration.ofHours(24);
    private static final Path HTTP_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "social-network-analysis-http-uploads");
    private static final Path TASK_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "social-network-analysis-uploads");
    private static final long MAX_RETRY_DELAY_SECONDS = 3600;

    private final Clock clock;
    private final DirectoryDeleter directoryDeleter;
    private final Map<Path, String> managedRoots = new ConcurrentHashMap<>();
    private final Set<Path> activeDirectories = ConcurrentHashMap.newKeySet();
    private final Map<Path, RetryState> failedDeletes = new ConcurrentHashMap<>();

    @Autowired
    public SocialNetworkUploadTemporaryFileCleanup() {
        this(HTTP_ROOT, TASK_ROOT, Clock.systemUTC(), deleteTree());
    }

    SocialNetworkUploadTemporaryFileCleanup(
            Path httpRoot,
            Path taskRoot,
            Clock clock,
            DirectoryDeleter directoryDeleter) {
        this.clock = clock;
        this.directoryDeleter = directoryDeleter;
        registerManagedRoot(httpRoot, "request-");
        registerManagedRoot(taskRoot, "task-");
    }

    SocialNetworkUploadTemporaryFileCleanup(
            Path httpRoot, Path taskRoot, Clock clock) {
        this(httpRoot, taskRoot, clock, deleteTree());
    }

    void registerManagedRoot(Path root, String directoryPrefix) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        String previous = managedRoots.putIfAbsent(normalizedRoot, directoryPrefix);
        if (previous != null && !previous.equals(directoryPrefix)) {
            throw new IllegalArgumentException("managed upload root prefix cannot change");
        }
    }

    void markActiveDirectory(Path directory) {
        activeDirectories.add(requireManagedDirectory(directory));
    }

    boolean deleteManagedDirectory(Path directory) {
        if (directory == null) {
            return true;
        }
        Path normalizedDirectory = requireManagedDirectory(directory);
        activeDirectories.remove(normalizedDirectory);
        try {
            directoryDeleter.delete(normalizedDirectory);
            failedDeletes.remove(normalizedDirectory);
            return true;
        } catch (IOException | SecurityException exception) {
            queueRetry(normalizedDirectory, exception);
            return false;
        }
    }

    @Scheduled(fixedDelayString = "${social-network.upload.cleanup-interval-ms:60000}")
    public void cleanupStaleDirectories() {
        retryFailedDeletes();
        deleteExpiredOrphans();
    }

    int pendingDeleteCount() {
        return failedDeletes.size();
    }

    private void retryFailedDeletes() {
        Instant now = clock.instant();
        failedDeletes.forEach((directory, retry) -> {
            if (!retry.nextAttempt().isAfter(now)) {
                deleteManagedDirectory(directory);
            }
        });
    }

    private void deleteExpiredOrphans() {
        Instant cutoff = clock.instant().minus(ORPHAN_RETENTION);
        managedRoots.forEach((root, prefix) -> {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            try (var entries = Files.newDirectoryStream(root)) {
                for (Path entry : entries) {
                    Path directory = entry.toAbsolutePath().normalize();
                    if (!directory.getFileName().toString().startsWith(prefix)
                            || activeDirectories.contains(directory)
                            || failedDeletes.containsKey(directory)
                            || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    Instant lastModified = Files.getLastModifiedTime(
                            directory, LinkOption.NOFOLLOW_LINKS).toInstant();
                    if (!lastModified.isAfter(cutoff)) {
                        deleteManagedDirectory(directory);
                    }
                }
            } catch (IOException | SecurityException exception) {
                LOGGER.warn(
                        "Unable to inspect a social-network upload temporary directory root; "
                                + "cleanup will retry. root={}",
                        root,
                        exception);
            }
        });
    }

    private void queueRetry(Path directory, Exception exception) {
        RetryState previous = failedDeletes.get(directory);
        int attempts = previous == null ? 1 : previous.attempts() + 1;
        long delaySeconds = Math.min(
                MAX_RETRY_DELAY_SECONDS,
                1L << Math.min(attempts - 1, 12));
        failedDeletes.put(
                directory,
                new RetryState(attempts, clock.instant().plusSeconds(delaySeconds)));
        LOGGER.warn(
                "Unable to remove a social-network upload temporary directory; "
                        + "cleanup retry queued. directory={}",
                directory,
                exception);
    }

    private Path requireManagedDirectory(Path directory) {
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Path root = normalizedDirectory.getParent();
        String prefix = managedRoots.get(root);
        if (prefix == null
                || normalizedDirectory.getFileName() == null
                || !normalizedDirectory.getFileName().toString().startsWith(prefix)) {
            throw new IllegalArgumentException(
                    "temporary directory is outside the managed upload roots");
        }
        return normalizedDirectory;
    }

    private static DirectoryDeleter deleteTree() {
        return root -> {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                        throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                        throws IOException {
                    if (exception != null) {
                        throw exception;
                    }
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        };
    }

    @FunctionalInterface
    interface DirectoryDeleter {
        void delete(Path directory) throws IOException;
    }

    private record RetryState(int attempts, Instant nextAttempt) {
    }
}
