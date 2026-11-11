package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.cache.SocialNetworkAnalysisResultCache;
import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.DatasetStatus;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.domain.SourceFormat;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedTweet;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.reader.FileReadIssue;
import com.prediction.socialnetwork.reader.FileReadReport;
import com.prediction.socialnetwork.reader.FileReadResult;
import com.prediction.socialnetwork.reader.RawRecord;
import com.prediction.socialnetwork.reader.SocialNetworkFileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SocialNetworkUploadTaskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocialNetworkUploadTaskService.class);
    private static final int MAX_FILES = 10;
    private static final long MAX_FILE_BYTES = 128L * 1024 * 1024;
    private static final long MAX_REQUEST_BYTES = 160L * 1024 * 1024;
    private static final Duration TERMINAL_TASK_RETENTION = Duration.ofHours(24);
    private static final Duration EXPIRED_TASK_MARKER_RETENTION = Duration.ofHours(24);
    private static final String FILE_BLOCKED = "SN_UPLOAD_FILE_BLOCKED";
    private static final String UPLOAD_FAILED = "SN_DATASET_UPLOAD_FAILED";

    private final SocialNetworkDatasetRegistry registry;
    private final SocialNetworkWorkspaceService workspace;
    private final SocialNetworkFileReader fileReader;
    private final SocialNetworkDataFingerprintService fingerprintService;
    private final SocialNetworkAnalysisResultCache analysisResultCache;
    private final Executor executor;
    private final Path stagingRoot;
    private final SocialNetworkUploadTemporaryFileCleanup temporaryFileCleanup;
    private final Map<String, TaskEntry> tasks = new ConcurrentHashMap<>();
    private final Map<String, Instant> expiredTaskIds = new ConcurrentHashMap<>();
    private final Map<String, Path> sessionStagingDirectories = new ConcurrentHashMap<>();

    @Autowired
    public SocialNetworkUploadTaskService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            @Qualifier("socialNetworkUploadExecutor") Executor executor,
            SocialNetworkUploadTemporaryFileCleanup temporaryFileCleanup,
            SocialNetworkDataFingerprintService fingerprintService,
            SocialNetworkAnalysisResultCache analysisResultCache) {
        this(
                registry,
                workspace,
                executor,
                Path.of(System.getProperty("java.io.tmpdir"), "social-network-analysis-uploads"),
                temporaryFileCleanup,
                fingerprintService,
                analysisResultCache);
    }

    SocialNetworkUploadTaskService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            Executor executor,
            Path stagingRoot) {
        this(
                registry,
                workspace,
                executor,
                stagingRoot,
                new SocialNetworkUploadTemporaryFileCleanup(
                        Path.of(
                                System.getProperty("java.io.tmpdir"),
                                "social-network-analysis-http-uploads"),
                        stagingRoot,
                        Clock.systemUTC()),
                new SocialNetworkDataFingerprintService(),
                new SocialNetworkAnalysisResultCache());
    }

    SocialNetworkUploadTaskService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            Executor executor,
            Path stagingRoot,
            SocialNetworkUploadTemporaryFileCleanup temporaryFileCleanup) {
        this(
                registry,
                workspace,
                executor,
                stagingRoot,
                temporaryFileCleanup,
                new SocialNetworkDataFingerprintService(),
                new SocialNetworkAnalysisResultCache());
    }

    SocialNetworkUploadTaskService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            Executor executor,
            Path stagingRoot,
            SocialNetworkUploadTemporaryFileCleanup temporaryFileCleanup,
            SocialNetworkDataFingerprintService fingerprintService) {
        this(
                registry,
                workspace,
                executor,
                stagingRoot,
                temporaryFileCleanup,
                fingerprintService,
                new SocialNetworkAnalysisResultCache());
    }

    SocialNetworkUploadTaskService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            Executor executor,
            Path stagingRoot,
            SocialNetworkUploadTemporaryFileCleanup temporaryFileCleanup,
            SocialNetworkDataFingerprintService fingerprintService,
            SocialNetworkAnalysisResultCache analysisResultCache) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.fileReader = new SocialNetworkFileReader();
        this.fingerprintService =
                Objects.requireNonNull(fingerprintService, "fingerprintService");
        this.analysisResultCache =
                Objects.requireNonNull(analysisResultCache, "analysisResultCache");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.stagingRoot = Objects.requireNonNull(stagingRoot, "stagingRoot");
        this.temporaryFileCleanup =
                Objects.requireNonNull(temporaryFileCleanup, "temporaryFileCleanup");
        this.temporaryFileCleanup.registerManagedRoot(this.stagingRoot, "task-");
    }

    public SocialNetworkUploadTaskView submit(
            List<SocialNetworkUploadFileInput> files, String sourceDescription) {
        List<SocialNetworkUploadFileInput> validatedFiles = validateSubmission(files);
        Path taskDirectory = null;
        List<StagedFile> stagedFiles = new ArrayList<>();
        try {
            Files.createDirectories(stagingRoot);
            taskDirectory = Files.createTempDirectory(stagingRoot, "task-");
            temporaryFileCleanup.markActiveDirectory(taskDirectory);
            long requestBytes = 0L;
            for (int index = 0; index < validatedFiles.size(); index++) {
                SocialNetworkUploadFileInput input = validatedFiles.get(index);
                long fileBytes = Files.size(input.stagedPath());
                if (fileBytes > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("uploaded file exceeds the per-file limit");
                }
                requestBytes = Math.addExact(requestBytes, fileBytes);
                if (requestBytes > MAX_REQUEST_BYTES) {
                    throw new IllegalArgumentException("upload request exceeds the total size limit");
                }
                String fileName = safeFileName(input.fileName());
                SourceFormat format = formatOf(fileName);
                Path destination = taskDirectory.resolve(
                        String.format(Locale.ROOT, "part-%02d.%s", index + 1, extensionOf(fileName)));
                Files.copy(input.stagedPath(), destination, StandardCopyOption.COPY_ATTRIBUTES);
                String logicalName = String.format(
                        Locale.ROOT, "file-%02d-%s", index + 1, fileName);
                DatasetFileDescriptor descriptor =
                        new DatasetFileDescriptor(logicalName, input.role(), format, "utf8");
                stagedFiles.add(new StagedFile(
                        input.partId(),
                        fileName,
                        input.role(),
                        format,
                        destination,
                        fileBytes,
                        descriptor));
            }
        } catch (IOException | ArithmeticException exception) {
            temporaryFileCleanup.deleteManagedDirectory(taskDirectory);
            throw new IllegalStateException("unable to stage upload files", exception);
        } catch (RuntimeException exception) {
            temporaryFileCleanup.deleteManagedDirectory(taskDirectory);
            throw exception;
        }

        TaskEntry task = new TaskEntry(
                UUID.randomUUID().toString(),
                taskDirectory,
                List.copyOf(stagedFiles),
                normalizeSourceDescription(sourceDescription));
        tasks.put(task.uploadTaskId, task);
        try {
            executor.execute(() -> process(task));
        } catch (RejectedExecutionException exception) {
            fail(task, UPLOAD_FAILED, "当前无法处理上传任务，请稍后重试。", List.of());
            cleanupTaskFiles(task);
        }
        return view(task);
    }

    public SocialNetworkUploadTaskView getTask(String uploadTaskId) {
        TaskEntry task = requireTask(uploadTaskId);
        expireReadyTaskIfNeeded(task);
        return view(task);
    }

    public SocialNetworkUploadTaskView cancelTask(String uploadTaskId) {
        TaskEntry task = requireTask(uploadTaskId);
        synchronized (task) {
            switch (task.status) {
                case QUEUED, PROCESSING -> {
                    task.cancelRequested = true;
                    task.status = TaskStatus.CANCELLING;
                    task.phase = null;
                    task.updatedAt = Instant.now();
                }
                case CANCELLING, CANCELLED -> {
                    return view(task);
                }
                default -> throw new IllegalStateException(
                        "upload task cannot be cancelled in its current state");
            }
        }
        return view(task);
    }

    public void closeSession(String datasetId, String datasetVersion, String sessionId) {
        registry.closeUploadSession(datasetId, datasetVersion, sessionId);
        analysisResultCache.removeSession(sessionId);
        workspace.removeUploadSnapshot(sessionId);
        Path stagingDirectory = sessionStagingDirectories.remove(sessionId);
        temporaryFileCleanup.deleteManagedDirectory(stagingDirectory);
    }

    private void process(TaskEntry task) {
        if (!start(task)) {
            cancelAndCleanup(task);
            return;
        }
        SocialNetworkDataset registeredDataset = null;
        try {
            List<FileReadResult> fileResults = new ArrayList<>();
            for (StagedFile file : task.files) {
                checkCancellation(task);
                updatePhase(task, "reading_files");
                FileReadResult result = fileReader.read(file.path(), file.descriptor(), false);
                fileResults.add(result);
                if (isFileBlocked(result.report())) {
                    throw blockedFileFailure(file, result.report());
                }
            }
            checkCancellation(task);
            if (fileResults.stream().mapToInt(result -> result.report().readOkCount()).sum() == 0) {
                throw new UploadFailure(
                        FILE_BLOCKED,
                        "所选文件没有可用于预览的有效记录。",
                        List.of());
            }
            task.fileReports = fileResults.stream().map(FileReadResult::report).toList();

            updatePhase(task, "merging_records");
            List<DatasetFileDescriptor> descriptors =
                    task.files.stream().map(StagedFile::descriptor).toList();
            String sourceDescription;
            synchronized (task) {
                sourceDescription = task.sourceDescription;
                task.sourceDescription = null;
            }
            SocialNetworkDataset candidate =
                    registry.prepareUploadDataset(descriptors, sourceDescription);
            SocialNetworkDataFingerprintService.Fingerprint inputFingerprint =
                    fingerprintService.fingerprintUpload(
                            candidate, task.stagingDirectory, stagedInputs(task.files));
            List<RawRecord> users = recordsForRole(task.files, fileResults, DatasetFileRole.USERS);
            List<RawRecord> relationships =
                    recordsForRole(task.files, fileResults, DatasetFileRole.RELATIONSHIPS);
            List<RawRecord> tweets = recordsForRole(task.files, fileResults, DatasetFileRole.TWEETS);

            checkCancellation(task);
            updatePhase(task, "normalizing");
            SocialNetworkWorkspaceSnapshot snapshot = workspace.prepareUploadSnapshot(
                    candidate,
                    users,
                    relationships,
                    tweets,
                    fileResults.stream().map(FileReadResult::report).toList())
                    .withInputFingerprint(inputFingerprint);
            if (snapshot.quality().datasetStatus() == DatasetStatus.UNAVAILABLE
                    || !hasUsableRecords(snapshot)) {
                throw new UploadFailure(
                        FILE_BLOCKED,
                        "规范化后没有可用于分析的有效记录。",
                        qualityIssues(task.files, fileResults));
            }

            checkCancellation(task);
            updatePhase(task, "registering_session");
            synchronized (task) {
                if (task.cancelRequested) {
                    throw new UploadCancelled();
                }
                registeredDataset = registry.registerUploadSession(snapshot.dataset());
                try {
                    workspace.registerUploadSnapshot(snapshot);
                } catch (RuntimeException exception) {
                    registry.closeUploadSession(registeredDataset.sessionId());
                    registeredDataset = null;
                    throw exception;
                }
                sessionStagingDirectories.put(
                        registeredDataset.sessionId(), task.stagingDirectory);
                task.preview = preview(task, snapshot);
                task.status = TaskStatus.READY;
                task.phase = null;
                task.updatedAt = Instant.now();
            }
        } catch (UploadCancelled exception) {
            if (registeredDataset != null) {
                closeRegisteredSession(registeredDataset);
            }
            cancelAndCleanup(task);
        } catch (UploadFailure exception) {
            fail(task, exception.code, exception.getMessage(), exception.issues);
            cleanupTaskFiles(task);
        } catch (Exception exception) {
            if (registeredDataset != null) {
                closeRegisteredSession(registeredDataset);
            }
            fail(task, UPLOAD_FAILED, "上传数据处理失败，请检查文件后重试。", List.of());
            cleanupTaskFiles(task);
        }
    }

    private List<SocialNetworkUploadFileInput> validateSubmission(
            List<SocialNetworkUploadFileInput> files) {
        Objects.requireNonNull(files, "files");
        if (files.isEmpty() || files.size() > MAX_FILES) {
            throw new IllegalArgumentException("upload must contain between 1 and 10 files");
        }
        Set<String> partIds = new HashSet<>();
        for (SocialNetworkUploadFileInput file : files) {
            Objects.requireNonNull(file, "file");
            if (!partIds.add(file.partId())) {
                throw new IllegalArgumentException("partId values must be unique");
            }
            if (file.role() == DatasetFileRole.UNKNOWN) {
                throw new IllegalArgumentException("unknown upload file role");
            }
            if (!Files.isRegularFile(file.stagedPath(), LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(file.stagedPath())) {
                throw new IllegalArgumentException("staged upload file is not readable");
            }
            formatOf(safeFileName(file.fileName()));
        }
        return List.copyOf(files);
    }

    private static SourceFormat formatOf(String fileName) {
        String extension = extensionOf(fileName).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "csv" -> SourceFormat.CSV;
            case "jsonl" -> SourceFormat.JSONL;
            default -> throw new IllegalArgumentException("only CSV and JSONL files are supported");
        };
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new IllegalArgumentException("uploaded file extension is missing");
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String safeFileName(String fileName) {
        String normalized = fileName.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String baseName = normalized.substring(separator + 1)
                .replaceAll("[\\p{Cntrl}]", "_")
                .trim();
        if (baseName.isBlank() || ".".equals(baseName) || "..".equals(baseName)) {
            throw new IllegalArgumentException("uploaded file name is invalid");
        }
        return baseName;
    }

    private static String normalizeSourceDescription(String sourceDescription) {
        return sourceDescription == null || sourceDescription.isBlank()
                ? "用户未提供"
                : sourceDescription.trim();
    }

    private static boolean isFileBlocked(FileReadReport report) {
        return report.headerStatus().name().equals("MISSING")
                || report.headerStatus().name().equals("INVALID")
                || report.rowCount() == 0
                || (report.readErrorCount() > 0 && report.readOkCount() == 0);
    }

    private static UploadFailure blockedFileFailure(
            StagedFile file, FileReadReport report) {
        List<SocialNetworkUploadTaskView.Issue> issues = report.issues().stream()
                .map(issue -> issue(file, issue))
                .toList();
        return new UploadFailure(
                FILE_BLOCKED,
                "文件“" + file.fileName() + "”未通过文件级校验。",
                issues);
    }

    private static List<SocialNetworkUploadTaskView.Issue> qualityIssues(
            List<StagedFile> files, List<FileReadResult> results) {
        List<SocialNetworkUploadTaskView.Issue> issues = new ArrayList<>();
        for (int index = 0; index < Math.min(files.size(), results.size()); index++) {
            StagedFile file = files.get(index);
            results.get(index).report().issues().stream()
                    .map(issue -> issue(file, issue))
                    .forEach(issues::add);
        }
        return List.copyOf(issues);
    }

    private static SocialNetworkUploadTaskView.Issue issue(
            StagedFile file, FileReadIssue issue) {
        List<Integer> sourceRows = issue.sourceRowNumber() > 0
                ? List.of(issue.sourceRowNumber())
                : List.of();
        return new SocialNetworkUploadTaskView.Issue(
                file.partId(), file.fileName(), issue.code(), sourceRows);
    }

    private static List<RawRecord> recordsForRole(
            List<StagedFile> files,
            List<FileReadResult> results,
            DatasetFileRole role) {
        List<RawRecord> records = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            if (files.get(index).role() == role) {
                records.addAll(results.get(index).records());
            }
        }
        return List.copyOf(records);
    }

    private static Map<String, Path> stagedInputs(List<StagedFile> files) {
        Map<String, Path> stagedInputs = new HashMap<>();
        for (StagedFile file : files) {
            if (stagedInputs.put(file.descriptor().logicalFileName(), file.path()) != null) {
                throw new IllegalArgumentException(
                        "upload contains duplicate logical file names");
            }
        }
        return Map.copyOf(stagedInputs);
    }

    private static boolean hasUsableRecords(SocialNetworkWorkspaceSnapshot snapshot) {
        boolean hasUser = snapshot.users().stream()
                .anyMatch(user -> user.userId() != null
                        && !user.userId().isBlank()
                        && user.normalizationStatus() != NormalizationStatus.REJECTED_REQUIRED_FIELD
                        && user.normalizationStatus() != NormalizationStatus.REJECTED_INVALID_FORMAT);
        boolean hasRelationship = snapshot.relationships().stream()
                .anyMatch(relationship -> relationship.analysisIncluded());
        boolean hasTweet = snapshot.tweets().stream().anyMatch(NormalizedTweet::analysisIncluded);
        return hasUser || hasRelationship || hasTweet;
    }

    private SocialNetworkUploadTaskView.Preview preview(
            TaskEntry task, SocialNetworkWorkspaceSnapshot snapshot) {
        List<SocialNetworkUploadTaskView.FileSummary> files = new ArrayList<>();
        for (int index = 0; index < task.files.size(); index++) {
            StagedFile staged = task.files.get(index);
            FileReadReport report = task.fileReports.get(index);
            files.add(new SocialNetworkUploadTaskView.FileSummary(
                    staged.partId(),
                    staged.fileName(),
                    staged.role(),
                    staged.format(),
                    report.rowCount(),
                    report.readOkCount(),
                    report.readSkippedCount(),
                    report.readErrorCount(),
                    report.isUsable() ? "usable" : "limited"));
        }
        SocialNetworkDataset dataset = snapshot.dataset();
        return new SocialNetworkUploadTaskView.Preview(
                dataset.sessionId(),
                dataset.datasetId(),
                dataset.datasetVersion(),
                "upload_session",
                registry.sessionExpiresAt(dataset.sessionId()),
                snapshot.quality(),
                snapshot.capabilities(),
                files);
    }

    private boolean start(TaskEntry task) {
        synchronized (task) {
            if (task.cancelRequested || task.status == TaskStatus.CANCELLING) {
                return false;
            }
            if (task.status != TaskStatus.QUEUED) {
                return false;
            }
            task.status = TaskStatus.PROCESSING;
            task.phase = "validating_files";
            task.updatedAt = Instant.now();
            return true;
        }
    }

    private static void updatePhase(TaskEntry task, String phase) {
        synchronized (task) {
            if (task.cancelRequested || task.status == TaskStatus.CANCELLING) {
                throw new UploadCancelled();
            }
            task.phase = phase;
            task.updatedAt = Instant.now();
        }
    }

    private static void checkCancellation(TaskEntry task) {
        if (task.cancelRequested || task.status == TaskStatus.CANCELLING) {
            throw new UploadCancelled();
        }
    }

    private void cancelAndCleanup(TaskEntry task) {
        synchronized (task) {
            task.status = TaskStatus.CANCELLED;
            task.phase = null;
            task.sourceDescription = null;
            task.updatedAt = Instant.now();
        }
        cleanupTaskFiles(task);
    }

    private void fail(
            TaskEntry task,
            String code,
            String message,
            List<SocialNetworkUploadTaskView.Issue> issues) {
        synchronized (task) {
            task.status = TaskStatus.FAILED;
            task.phase = null;
            task.sourceDescription = null;
            task.error = new SocialNetworkUploadTaskView.Failure(code, message, issues);
            task.updatedAt = Instant.now();
        }
    }

    private void closeRegisteredSession(SocialNetworkDataset dataset) {
        analysisResultCache.removeSession(dataset.sessionId());
        workspace.removeUploadSnapshot(dataset.sessionId());
        registry.closeUploadSession(dataset.sessionId());
        sessionStagingDirectories.remove(dataset.sessionId());
    }

    private TaskEntry requireTask(String uploadTaskId) {
        TaskEntry task = tasks.get(uploadTaskId);
        if (task == null) {
            Instant markerExpiresAt = expiredTaskIds.get(uploadTaskId);
            if (markerExpiresAt != null && markerExpiresAt.isAfter(Instant.now())) {
                throw new SocialNetworkUploadTaskExpiredException();
            }
            throw new IllegalArgumentException("unknown upload task");
        }
        return task;
    }

    private void expireReadyTaskIfNeeded(TaskEntry task) {
        synchronized (task) {
            SocialNetworkUploadTaskView.Preview preview = task.preview;
            if (task.status != TaskStatus.READY || preview == null) {
                return;
            }
            try {
                registry.resolveDatasetContext(
                        preview.datasetId(), preview.datasetVersion(), preview.sessionId());
            } catch (SocialNetworkDatasetSessionExpiredException exception) {
                registry.expireSessions();
                analysisResultCache.removeSession(preview.sessionId());
                workspace.removeUploadSnapshot(preview.sessionId());
                temporaryFileCleanup.deleteManagedDirectory(
                        sessionStagingDirectories.remove(preview.sessionId()));
                task.preview = null;
                task.status = TaskStatus.EXPIRED;
                task.phase = null;
                task.error = new SocialNetworkUploadTaskView.Failure(
                        "SN_UPLOAD_TASK_EXPIRED",
                        "上传任务对应的分析预览已过期，请重新上传。",
                        List.of());
                task.updatedAt = Instant.now();
            }
        }
    }

    @Scheduled(fixedDelayString = "${social-network.upload.cleanup-interval-ms:60000}")
    public void expireSessionsAndCleanStaging() {
        registry.expireSessions();
        for (TaskEntry task : tasks.values()) {
            try {
                expireReadyTaskIfNeeded(task);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Unable to expire an upload preview; cleanup will retry. taskId={}",
                        task.uploadTaskId,
                        exception);
            }
        }
        cleanupExpiredTaskRecords(Instant.now());
    }

    void cleanupExpiredTaskRecords(Instant now) {
        for (TaskEntry task : tasks.values()) {
            synchronized (task) {
                if (!isTerminal(task.status)
                        || task.updatedAt.plus(TERMINAL_TASK_RETENTION).isAfter(now)) {
                    continue;
                }
                expiredTaskIds.put(
                        task.uploadTaskId,
                        now.plus(EXPIRED_TASK_MARKER_RETENTION));
                tasks.remove(task.uploadTaskId, task);
            }
        }
        expiredTaskIds.forEach((taskId, expiresAt) -> {
            if (!expiresAt.isAfter(now)) {
                expiredTaskIds.remove(taskId, expiresAt);
            }
        });
    }

    private static boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.FAILED
                || status == TaskStatus.CANCELLED
                || status == TaskStatus.EXPIRED;
    }

    private static SocialNetworkUploadTaskView view(TaskEntry task) {
        synchronized (task) {
            return new SocialNetworkUploadTaskView(
                    task.uploadTaskId,
                    task.status.name().toLowerCase(Locale.ROOT),
                    task.phase,
                    task.updatedAt,
                    task.preview,
                    task.error);
        }
    }

    private void cleanupTaskFiles(TaskEntry task) {
        if (task.preview != null) {
            sessionStagingDirectories.remove(task.preview.sessionId());
        }
        temporaryFileCleanup.deleteManagedDirectory(task.stagingDirectory);
    }

    private record StagedFile(
            String partId,
            String fileName,
            DatasetFileRole role,
            SourceFormat format,
            Path path,
            long sizeBytes,
            DatasetFileDescriptor descriptor) {
    }

    private enum TaskStatus {
        QUEUED,
        PROCESSING,
        READY,
        FAILED,
        CANCELLING,
        CANCELLED,
        EXPIRED
    }

    private static final class TaskEntry {
        private final String uploadTaskId;
        private final Path stagingDirectory;
        private final List<StagedFile> files;
        private String sourceDescription;
        private final Instant createdAt = Instant.now();
        private volatile Instant updatedAt = createdAt;
        private volatile TaskStatus status = TaskStatus.QUEUED;
        private volatile String phase;
        private volatile boolean cancelRequested;
        private volatile List<FileReadReport> fileReports = List.of();
        private volatile SocialNetworkUploadTaskView.Preview preview;
        private volatile SocialNetworkUploadTaskView.Failure error;

        private TaskEntry(
                String uploadTaskId,
                Path stagingDirectory,
                List<StagedFile> files,
                String sourceDescription) {
            this.uploadTaskId = uploadTaskId;
            this.stagingDirectory = stagingDirectory;
            this.files = files;
            this.sourceDescription = sourceDescription;
        }
    }

    private static final class UploadFailure extends RuntimeException {
        private final String code;
        private final List<SocialNetworkUploadTaskView.Issue> issues;

        private UploadFailure(
                String code,
                String message,
                List<SocialNetworkUploadTaskView.Issue> issues) {
            super(message);
            this.code = code;
            this.issues = List.copyOf(issues);
        }
    }

    private static final class UploadCancelled extends RuntimeException {
    }
}
