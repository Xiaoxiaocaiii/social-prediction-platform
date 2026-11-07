package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SocialNetworkUploadTemporaryFileCleanupTest {

    @TempDir
    Path tempDir;

    @Test
    void removesOnlyExpiredUntrackedDirectoriesFromManagedRoots() throws IOException {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-18T12:00:00Z"));
        Path httpRoot = Files.createDirectory(tempDir.resolve("http"));
        Path taskRoot = Files.createDirectory(tempDir.resolve("tasks"));
        SocialNetworkUploadTemporaryFileCleanup cleanup =
                new SocialNetworkUploadTemporaryFileCleanup(httpRoot, taskRoot, clock);
        Path expiredRequest = createDirectory(httpRoot, "request-expired");
        Path activeTask = createDirectory(taskRoot, "task-active");
        Path recentTask = createDirectory(taskRoot, "task-recent");
        Path unrelated = createDirectory(taskRoot, "other-data");
        markOlderThanRetention(expiredRequest, clock);
        markOlderThanRetention(activeTask, clock);
        cleanup.markActiveDirectory(activeTask);

        cleanup.cleanupStaleDirectories();

        assertFalse(Files.exists(expiredRequest));
        assertTrue(Files.isDirectory(activeTask));
        assertTrue(Files.isDirectory(recentTask));
        assertTrue(Files.isDirectory(unrelated));
    }

    @Test
    void retriesFailedDeletionWithBackoff() throws IOException {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-18T12:00:00Z"));
        Path httpRoot = Files.createDirectory(tempDir.resolve("retry-http"));
        Path taskRoot = Files.createDirectory(tempDir.resolve("retry-tasks"));
        AtomicInteger attempts = new AtomicInteger();
        SocialNetworkUploadTemporaryFileCleanup cleanup =
                new SocialNetworkUploadTemporaryFileCleanup(
                        httpRoot,
                        taskRoot,
                        clock,
                        directory -> {
                            if (attempts.getAndIncrement() == 0) {
                                throw new IOException("simulated delete failure");
                            }
                            deleteTree(directory);
                        });
        Path directory = createDirectory(taskRoot, "task-retry");

        assertFalse(cleanup.deleteManagedDirectory(directory));
        assertTrue(Files.isDirectory(directory));
        assertEquals(1, cleanup.pendingDeleteCount());

        clock.advanceSeconds(1);
        cleanup.cleanupStaleDirectories();

        assertFalse(Files.exists(directory));
        assertEquals(0, cleanup.pendingDeleteCount());
        assertEquals(2, attempts.get());
    }

    @Test
    void refusesToDeleteDirectoriesOutsideManagedRoots() throws IOException {
        Path httpRoot = Files.createDirectory(tempDir.resolve("safe-http"));
        Path taskRoot = Files.createDirectory(tempDir.resolve("safe-tasks"));
        SocialNetworkUploadTemporaryFileCleanup cleanup =
                new SocialNetworkUploadTemporaryFileCleanup(
                        httpRoot, taskRoot, Clock.systemUTC());
        Path unrelated = createDirectory(tempDir, "task-not-managed");

        assertThrows(
                IllegalArgumentException.class,
                () -> cleanup.deleteManagedDirectory(unrelated));
        assertTrue(Files.isDirectory(unrelated));
    }

    private static Path createDirectory(Path root, String name) throws IOException {
        Path directory = Files.createDirectory(root.resolve(name));
        Files.writeString(directory.resolve("part.tmp"), "temporary");
        return directory;
    }

    private static void markOlderThanRetention(Path directory, MutableClock clock)
            throws IOException {
        Files.setLastModifiedTime(
                directory,
                FileTime.from(clock.instant().minusSeconds(25 * 60 * 60L)));
    }

    private static void deleteTree(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted((left, right) ->
                    right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        private void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }
}
