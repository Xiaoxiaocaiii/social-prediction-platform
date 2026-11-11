package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.cache.CacheResultType;
import com.prediction.socialnetwork.cache.SocialNetworkAnalysisResultCache;
import com.prediction.socialnetwork.cache.SocialNetworkCacheKey;
import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.SourceFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SocialNetworkUploadTaskServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void registersSessionOnlyAfterBuildingAnIsolatedPreview() throws IOException {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(registry, workspace, Runnable::run, tempDir.resolve("staging"));
        Path firstFile = writeUsersFile("first.csv", "user_id,username\nuser-1,first\n");
        Path secondFile = writeUsersFile("second.csv", "user_id,username\nuser-2,second\n");

        SocialNetworkUploadTaskView first = service.submit(
                List.of(input("first", "users.csv", firstFile)), null);
        SocialNetworkUploadTaskView second = service.submit(
                List.of(input("second", "users.csv", secondFile)), null);

        assertEquals("ready", first.status());
        assertNotNull(first.preview());
        assertEquals(1, first.preview().files().get(0).readOkCount());
        assertEquals("ready", second.status());
        assertEquals(
                "think-tank-social-network",
                registry.currentDataset().datasetId());

        SocialNetworkWorkspaceSnapshot firstSnapshot = workspace.get(
                first.preview().datasetId(),
                first.preview().datasetVersion(),
                first.preview().sessionId());
        SocialNetworkWorkspaceSnapshot secondSnapshot = workspace.get(
                second.preview().datasetId(),
                second.preview().datasetVersion(),
                second.preview().sessionId());
        assertEquals(List.of("user-1"), firstSnapshot.users().stream()
                .map(user -> user.userId()).toList());
        assertEquals(List.of("user-2"), secondSnapshot.users().stream()
                .map(user -> user.userId()).toList());

        service.closeSession(
                first.preview().datasetId(),
                first.preview().datasetVersion(),
                first.preview().sessionId());
        assertThrows(IllegalStateException.class, () -> workspace.get(
                first.preview().datasetId(),
                first.preview().datasetVersion(),
                first.preview().sessionId()));
        assertEquals(List.of("user-2"), workspace.get(
                second.preview().datasetId(),
                second.preview().datasetVersion(),
                second.preview().sessionId()).users().stream()
                .map(user -> user.userId()).toList());
        service.closeSession(
                first.preview().datasetId(),
                first.preview().datasetVersion(),
                first.preview().sessionId());
    }

    @Test
    void uploadSnapshotExposesNormalizedGraphInputAndFingerprintForMixedFiles()
            throws IOException {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(
                        registry, workspace, Runnable::run, tempDir.resolve("mixed-staging"));
        Path firstUsers = writeUsersFile(
                "mixed-users.csv", "user_id,username\nuser-1,first\n");
        Path secondUsers = writeUsersFile(
                "mixed-users.jsonl", "{\"user_id\":\"user-2\",\"username\":\"second\"}\n");
        Path relationships = writeUsersFile(
                "mixed-relationships.jsonl",
                "{\"source_user_id\":\"user-1\",\"target_user_id\":\"user-2\"}\n");

        SocialNetworkUploadTaskView task = service.submit(
                List.of(
                        input("users-csv", "users.csv", firstUsers),
                        input("users-jsonl", "users.jsonl", secondUsers),
                        new SocialNetworkUploadFileInput(
                                "relationships",
                                "relationships.jsonl",
                                DatasetFileRole.RELATIONSHIPS,
                                relationships)),
                null);

        assertEquals("ready", task.status());
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(
                task.preview().datasetId(),
                task.preview().datasetVersion(),
                task.preview().sessionId());
        SocialNetworkWorkspaceSnapshot.GraphAnalysisInput graphInput =
                snapshot.graphAnalysisInput();

        assertNotNull(graphInput.inputFingerprint());
        assertEquals(3, graphInput.inputFingerprint().files().size());
        assertEquals(2, graphInput.users().size());
        assertEquals(1, graphInput.relationships().stream()
                .filter(relationship -> relationship.analysisIncluded())
                .count());
        assertEquals(2, graphInput.network().nodes().size());
        service.closeSession(
                task.preview().datasetId(),
                task.preview().datasetVersion(),
                task.preview().sessionId());
    }

    @Test
    void fileLevelFailureDoesNotRegisterAnUploadSession() throws IOException {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        Path stagingRoot = tempDir.resolve("staging");
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(registry, workspace, Runnable::run, stagingRoot);
        Path invalidFile = writeUsersFile("invalid.csv", "wrong_column\nvalue\n");

        SocialNetworkUploadTaskView task = service.submit(
                List.of(input("invalid", "users.csv", invalidFile)), null);

        assertEquals("failed", task.status());
        assertEquals("SN_UPLOAD_FILE_BLOCKED", task.error().code());
        assertEquals("think-tank-social-network", registry.currentDataset().datasetId());
        assertTrue(Files.isDirectory(stagingRoot));
        try (var paths = Files.list(stagingRoot)) {
            assertEquals(0, paths.count());
        }
    }

    @Test
    void malformedEncodingBlocksTheWholeFileAndCleansStaging() throws IOException {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        Path stagingRoot = tempDir.resolve("invalid-encoding-staging");
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(registry, workspace, Runnable::run, stagingRoot);
        byte[] validPrefix = "user_id\nvalid-user\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] malformed = java.util.Arrays.copyOf(validPrefix, validPrefix.length + 2);
        malformed[validPrefix.length] = (byte) 0xC3;
        malformed[validPrefix.length + 1] = (byte) 0x28;
        Path invalidFile = tempDir.resolve("invalid-encoding.csv");
        Files.write(invalidFile, malformed);

        SocialNetworkUploadTaskView task = service.submit(
                List.of(input("invalid-encoding", "invalid-encoding.csv", invalidFile)), null);

        assertEquals("failed", task.status());
        assertEquals("SN_UPLOAD_FILE_BLOCKED", task.error().code());
        assertNull(task.preview());
        assertTrue(task.error().issues().stream()
                .anyMatch(issue -> "UNSUPPORTED_ENCODING".equals(issue.issueCode())));
        assertEquals("think-tank-social-network", registry.currentDataset().datasetId());
        assertEquals(0, countDirectories(stagingRoot));
    }

    @Test
    void malformedCsvAfterValidRowsDoesNotCreateSessionAndCleansStaging() throws IOException {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        Path stagingRoot = tempDir.resolve("malformed-csv-staging");
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(registry, workspace, Runnable::run, stagingRoot);
        Path malformedFile = writeUsersFile(
                "malformed.csv",
                "user_id,username\nvalid-user,valid\nbroken-user,\"PRIVATE-CSV-CONTENT-4ac2");

        SocialNetworkUploadTaskView task = service.submit(
                List.of(input("malformed", "malformed.csv", malformedFile)), null);

        assertEquals("failed", task.status());
        assertEquals("SN_UPLOAD_FILE_BLOCKED", task.error().code());
        assertNull(task.preview());
        assertTrue(task.error().issues().stream()
                .anyMatch(issue -> "CSV_PARSE_FAILED".equals(issue.issueCode())));
        assertEquals("think-tank-social-network", registry.currentDataset().datasetId());
        assertEquals(0, countDirectories(stagingRoot));
    }

    @Test
    void jsonlParseFailureDoesNotEchoSourceContentInPreviewIssues() throws IOException {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        Path stagingRoot = tempDir.resolve("sanitized-jsonl-staging");
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(registry, workspace, Runnable::run, stagingRoot);
        String secret = "PRIVATE-CONTENT-7f31";
        Path tweetsFile = tempDir.resolve("tweets-with-invalid-row.jsonl");
        Files.writeString(
                tweetsFile,
                "{\"tweet_id\":\"t1\",\"user_id\":\"u1\","
                        + "\"created_at\":\"2025-01-01T00:00:00Z\",\"text\":\"valid\"}\n"
                        + "{\"text\":\"" + secret);

        SocialNetworkUploadTaskView task = service.submit(
                List.of(new SocialNetworkUploadFileInput(
                        "tweets", "tweets.jsonl", DatasetFileRole.TWEETS, tweetsFile)),
                null);

        assertEquals("ready", task.status());
        assertTrue(task.preview().qualitySummary().issues().stream()
                .anyMatch(issue -> "JSONL_PARSE_FAILED".equals(issue.code())));
        assertTrue(task.preview().qualitySummary().issues().stream()
                .noneMatch(issue -> issue.message().contains(secret)));
        service.closeSession(
                task.preview().datasetId(),
                task.preview().datasetVersion(),
                task.preview().sessionId());
    }

    @Test
    void cancellingOneQueuedTaskDoesNotDeleteAnotherTasksStagingFiles() throws IOException {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        Path stagingRoot = tempDir.resolve("staging");
        List<Runnable> queuedJobs = new ArrayList<>();
        Executor queuedExecutor = queuedJobs::add;
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(registry, workspace, queuedExecutor, stagingRoot);
        Path firstFile = writeUsersFile("first.csv", "user_id\nuser-1\n");
        Path secondFile = writeUsersFile("second.csv", "user_id\nuser-2\n");

        SocialNetworkUploadTaskView first = service.submit(
                List.of(input("first", "first.csv", firstFile)), null);
        SocialNetworkUploadTaskView second = service.submit(
                List.of(input("second", "second.csv", secondFile)), null);

        assertEquals("queued", first.status());
        assertEquals("queued", second.status());
        assertEquals(2, countDirectories(stagingRoot));

        service.cancelTask(first.uploadTaskId());
        queuedJobs.get(0).run();

        assertEquals("cancelled", service.getTask(first.uploadTaskId()).status());
        assertEquals(1, countDirectories(stagingRoot));
        assertTrue(Files.exists(findOnlyStagedFile(stagingRoot)));

        queuedJobs.get(1).run();
        assertEquals("ready", service.getTask(second.uploadTaskId()).status());
        assertTrue(Files.exists(findOnlyStagedFile(stagingRoot).getParent()));
        service.closeSession(
                service.getTask(second.uploadTaskId()).preview().datasetId(),
                service.getTask(second.uploadTaskId()).preview().datasetVersion(),
                service.getTask(second.uploadTaskId()).preview().sessionId());
        assertEquals(0, countDirectories(stagingRoot));
    }

    @Test
    void readyTaskExpiresWithItsSessionAndDoesNotReplaceBuiltinData() throws IOException {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-17T12:00:00Z"));
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry(clock);
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        Path stagingRoot = tempDir.resolve("expiring-staging");
        SocialNetworkAnalysisResultCache analysisCache = new SocialNetworkAnalysisResultCache();
        SocialNetworkUploadTemporaryFileCleanup cleanup =
                new SocialNetworkUploadTemporaryFileCleanup(
                        tempDir.resolve("expiring-http"), stagingRoot, Clock.systemUTC());
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(
                        registry,
                        workspace,
                        Runnable::run,
                        stagingRoot,
                        cleanup,
                        new SocialNetworkDataFingerprintService(),
                        analysisCache);
        Path usersFile = writeUsersFile("expiring.csv", "user_id,username\nexpires-user,expires\n");

        SocialNetworkUploadTaskView ready = service.submit(
                List.of(input("expiring", "expiring.csv", usersFile)), null);

        assertEquals("ready", ready.status());
        assertEquals(1, countDirectories(stagingRoot));
        var cachedKey = new SocialNetworkCacheKey("expiring-key", "expiring-material");
        analysisCache.put(
                CacheResultType.LOUVAIN_ANALYSIS,
                cachedKey,
                ready.preview().sessionId(),
                "session-result",
                clock.instant());
        clock.advance(Duration.ofHours(2));

        SocialNetworkUploadTaskView expired = service.getTask(ready.uploadTaskId());

        assertEquals("expired", expired.status());
        assertEquals("SN_UPLOAD_TASK_EXPIRED", expired.error().code());
        assertNull(expired.preview());
        assertEquals("think-tank-social-network", registry.currentDataset().datasetId());
        assertEquals(0, countDirectories(stagingRoot));
        assertTrue(analysisCache.find(
                CacheResultType.LOUVAIN_ANALYSIS,
                cachedKey,
                String.class).isEmpty());
        assertThrows(SocialNetworkDatasetSessionExpiredException.class, () -> workspace.get(
                ready.preview().datasetId(),
                ready.preview().datasetVersion(),
                ready.preview().sessionId()));
    }

    @Test
    void closingUploadSessionEvictsOnlyItsAnalysisCacheEntries() {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        Path stagingRoot = tempDir.resolve("closing-session-staging");
        SocialNetworkAnalysisResultCache analysisCache = new SocialNetworkAnalysisResultCache();
        SocialNetworkUploadTemporaryFileCleanup cleanup =
                new SocialNetworkUploadTemporaryFileCleanup(
                        tempDir.resolve("closing-session-http"), stagingRoot, Clock.systemUTC());
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(
                        registry,
                        workspace,
                        Runnable::run,
                        stagingRoot,
                        cleanup,
                        new SocialNetworkDataFingerprintService(),
                        analysisCache);
        var uploaded = registry.createUploadSession(List.of(new DatasetFileDescriptor(
                "users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8")));
        var cachedKey = new SocialNetworkCacheKey("closing-key", "closing-material");
        analysisCache.put(
                CacheResultType.LOUVAIN_ANALYSIS,
                cachedKey,
                uploaded.sessionId(),
                "session-result",
                Instant.now());

        service.closeSession(
                uploaded.datasetId(), uploaded.datasetVersion(), uploaded.sessionId());

        assertFalse(analysisCache.find(
                CacheResultType.LOUVAIN_ANALYSIS,
                cachedKey,
                String.class).isPresent());
        assertEquals(
                "think-tank-social-network",
                registry.currentDataset().datasetId());
    }

    @Test
    void scheduledExpiryCleansSessionFilesWithoutClientPolling() throws IOException {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-17T12:00:00Z"));
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry(clock);
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        Path stagingRoot = tempDir.resolve("scheduled-expiring-staging");
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(
                        registry, workspace, Runnable::run, stagingRoot);
        Path usersFile = writeUsersFile(
                "scheduled-expiring.csv", "user_id,username\nscheduled-user,scheduled\n");

        SocialNetworkUploadTaskView ready = service.submit(
                List.of(input("scheduled-expiring", "scheduled-expiring.csv", usersFile)), null);
        assertEquals("ready", ready.status());
        assertEquals(1, countDirectories(stagingRoot));

        clock.advance(Duration.ofHours(2));
        service.expireSessionsAndCleanStaging();

        assertEquals("expired", service.getTask(ready.uploadTaskId()).status());
        assertEquals("think-tank-social-network", registry.currentDataset().datasetId());
        assertEquals(0, countDirectories(stagingRoot));
        assertThrows(SocialNetworkDatasetSessionExpiredException.class, () -> workspace.get(
                ready.preview().datasetId(),
                ready.preview().datasetVersion(),
                ready.preview().sessionId()));
    }

    @Test
    void removesOnlyTerminalTaskRecordsAfterRetentionAndKeepsExpiredMarkers() throws IOException {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        List<Runnable> queuedJobs = new ArrayList<>();
        Path stagingRoot = tempDir.resolve("task-record-retention-staging");
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(
                        registry, workspace, queuedJobs::add, stagingRoot);
        Path validFile = writeUsersFile("retained.csv", "user_id\nactive-user\n");
        Path invalidFile = writeUsersFile("failed.csv", "wrong_column\nvalue\n");

        SocialNetworkUploadTaskView active = service.submit(
                List.of(input("active", "active.csv", validFile)), null);
        SocialNetworkUploadTaskView failed = service.submit(
                List.of(input("failed", "failed.csv", invalidFile)), null);
        SocialNetworkUploadTaskView cancelled = service.submit(
                List.of(input("cancelled", "cancelled.csv", validFile)), null);
        queuedJobs.get(1).run();
        service.cancelTask(cancelled.uploadTaskId());
        queuedJobs.get(2).run();

        service.cleanupExpiredTaskRecords(Instant.now().plus(Duration.ofHours(25)));

        assertEquals("queued", service.getTask(active.uploadTaskId()).status());
        assertThrows(
                SocialNetworkUploadTaskExpiredException.class,
                () -> service.getTask(failed.uploadTaskId()));
        assertThrows(
                SocialNetworkUploadTaskExpiredException.class,
                () -> service.getTask(cancelled.uploadTaskId()));

        service.cleanupExpiredTaskRecords(Instant.now().plus(Duration.ofHours(50)));

        assertThrows(IllegalArgumentException.class, () -> service.getTask(failed.uploadTaskId()));
        assertEquals(1, countDirectories(stagingRoot));
        queuedJobs.get(0).run();
        service.closeSession(
                service.getTask(active.uploadTaskId()).preview().datasetId(),
                service.getTask(active.uploadTaskId()).preview().datasetVersion(),
                service.getTask(active.uploadTaskId()).preview().sessionId());
    }

    @Test
    void retainsExpiredReadyTaskBrieflyAfterSessionAndStagingAreRemoved() throws IOException {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        Path stagingRoot = tempDir.resolve("expired-task-record-staging");
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(registry, workspace, Runnable::run, stagingRoot);
        Path usersFile = writeUsersFile("expired-record.csv", "user_id\nexpired-user\n");
        SocialNetworkUploadTaskView ready = service.submit(
                List.of(input("expired-record", "expired-record.csv", usersFile)), null);

        service.closeSession(
                ready.preview().datasetId(),
                ready.preview().datasetVersion(),
                ready.preview().sessionId());
        service.expireSessionsAndCleanStaging();
        assertEquals("expired", service.getTask(ready.uploadTaskId()).status());
        assertEquals(0, countDirectories(stagingRoot));

        service.cleanupExpiredTaskRecords(Instant.now().plus(Duration.ofHours(25)));
        assertThrows(
                SocialNetworkUploadTaskExpiredException.class,
                () -> service.getTask(ready.uploadTaskId()));
    }

    @Test
    void rejectedUploadCleansStagingAndReturnsAFailedTask() throws IOException {
        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkWorkspaceService workspace = new SocialNetworkWorkspaceService(registry);
        Path stagingRoot = tempDir.resolve("rejected-task-staging");
        Executor saturatedExecutor = command -> {
            throw new RejectedExecutionException("executor queue is full");
        };
        SocialNetworkUploadTaskService service =
                new SocialNetworkUploadTaskService(
                        registry, workspace, saturatedExecutor, stagingRoot);
        Path usersFile = writeUsersFile("rejected.csv", "user_id\nrejected-user\n");

        SocialNetworkUploadTaskView rejected = service.submit(
                List.of(input("rejected", "rejected.csv", usersFile)), "transient note");

        assertEquals("failed", rejected.status());
        assertEquals("SN_DATASET_UPLOAD_FAILED", rejected.error().code());
        assertNull(rejected.preview());
        assertEquals(0, countDirectories(stagingRoot));
        assertEquals("think-tank-social-network", registry.currentDataset().datasetId());
    }

    private Path writeUsersFile(String name, String contents) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, contents);
        return file;
    }

    private static SocialNetworkUploadFileInput input(String partId, String fileName, Path path) {
        return new SocialNetworkUploadFileInput(partId, fileName, DatasetFileRole.USERS, path);
    }

    private static long countDirectories(Path root) throws IOException {
        try (var paths = Files.list(root)) {
            return paths.filter(Files::isDirectory).count();
        }
    }

    private static Path findOnlyStagedFile(Path root) throws IOException {
        try (var directories = Files.list(root)) {
            Path taskDirectory = directories.filter(Files::isDirectory).findFirst().orElseThrow();
            try (var files = Files.list(taskDirectory)) {
                return files.findFirst().orElseThrow();
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

        private void advance(Duration duration) {
            now = now.plus(duration);
        }
    }
}
