package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.DatasetStatus;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.domain.SourceFormat;
import com.prediction.socialnetwork.domain.UploadSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SocialNetworkDatasetRegistry {

    public static final String BUILTIN_DATASET_ID = "think-tank-social-network";
    public static final String BUILTIN_DATASET_VERSION = "v1";
    public static final String BUILTIN_DATASET_NAME = "智库社交网络数据";
    public static final String SOURCE_DESCRIPTION =
            "当前数据由项目开发者整理并提供，具体采集来源、时间范围、数据处理方式和授权说明以最终数据说明文档为准。";

    private static final String DATASET_DESCRIPTION = "智库等机构账号的用户关系与推文互动数据";
    private static final Duration IDLE_TTL = Duration.ofHours(2);
    private static final Duration HARD_TTL = Duration.ofHours(24);
    private static final List<DatasetFileDescriptor> BUILTIN_FILES = List.of(
            new DatasetFileDescriptor(
                    "all_csv/merged_dataset/all_user_info.csv",
                    DatasetFileRole.USERS,
                    SourceFormat.CSV,
                    "utf8"),
            new DatasetFileDescriptor(
                    "all_csv/merged_dataset/all_following_list.csv",
                    DatasetFileRole.RELATIONSHIPS,
                    SourceFormat.CSV,
                    "utf8"),
            new DatasetFileDescriptor(
                    "all_csv/merged_dataset/all_tweets_raw.csv",
                    DatasetFileRole.TWEETS,
                    SourceFormat.CSV,
                    "utf8"));

    private final Clock clock;
    private final Map<String, SocialNetworkDataset> datasets = new ConcurrentHashMap<>();
    private final Map<String, UploadSession> sessions = new ConcurrentHashMap<>();
    private volatile String currentDatasetId = BUILTIN_DATASET_ID;

    public SocialNetworkDatasetRegistry() {
        this(Clock.systemUTC());
    }

    SocialNetworkDatasetRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        registerBuiltin();
    }

    public SocialNetworkDataset registerBuiltin() {
        SocialNetworkDataset dataset = new SocialNetworkDataset(
                BUILTIN_DATASET_ID,
                BUILTIN_DATASET_NAME,
                DatasetType.BUILTIN,
                BUILTIN_DATASET_VERSION,
                DatasetStatus.AVAILABLE_WITH_WARNINGS,
                DATASET_DESCRIPTION,
                SOURCE_DESCRIPTION,
                LocalDate.of(2016, 5, 3),
                LocalDate.of(2026, 1, 21),
                BUILTIN_FILES,
                null,
                "normalization-v1",
                "network-metrics-v2.0",
                "text-processing-v1",
                "explanation-v1");
        datasets.put(dataset.datasetId(), dataset);
        return dataset;
    }

    public SocialNetworkDataset prepareUploadDataset(
            List<DatasetFileDescriptor> files, String sourceDescription) {
        Objects.requireNonNull(files, "files");
        if (files.isEmpty()) {
            throw new IllegalArgumentException("files must not be empty");
        }
        String sessionId = UUID.randomUUID().toString();
        String datasetId = "upload-" + sessionId;
        String normalizedSourceDescription = sourceDescription == null
                || sourceDescription.isBlank()
                ? "用户未提供"
                : sourceDescription.trim();
        return new SocialNetworkDataset(
                datasetId,
                "当前分析会话上传数据",
                DatasetType.UPLOAD_SESSION,
                "session-" + sessionId,
                DatasetStatus.PARTIAL,
                "用户上传的社交网络分析数据",
                normalizedSourceDescription,
                null,
                null,
                files,
                sessionId,
                "normalization-v1",
                "network-metrics-v2.0",
                "text-processing-v1",
                "explanation-v1");
    }

    public SocialNetworkDataset registerUploadSession(SocialNetworkDataset dataset) {
        Objects.requireNonNull(dataset, "dataset");
        if (!dataset.isUploadSession()) {
            throw new IllegalArgumentException("dataset must be an upload session");
        }
        Instant now = clock.instant();
        UploadSession session = new UploadSession(
                dataset.sessionId(),
                dataset.datasetId(),
                now,
                now,
                now.plus(IDLE_TTL),
                now.plus(HARD_TTL),
                UploadSession.SessionStatus.ACTIVE);
        if (sessions.putIfAbsent(session.sessionId(), session) != null) {
            throw new IllegalStateException("upload session already exists");
        }
        if (datasets.putIfAbsent(dataset.datasetId(), dataset) != null) {
            sessions.remove(session.sessionId(), session);
            throw new IllegalStateException("upload dataset already exists");
        }
        return dataset;
    }

    public SocialNetworkDataset createUploadSession(List<DatasetFileDescriptor> files) {
        return registerUploadSession(prepareUploadDataset(files, "用户未提供"));
    }

    public SocialNetworkDataset currentDataset() {
        return requireDataset(currentDatasetId);
    }

    public SocialNetworkDataset findDataset(String datasetId) {
        return requireDataset(datasetId);
    }

    public SocialNetworkDataset resolveDatasetContext(
            String datasetId, String datasetVersion, String sessionId) {
        if (sessionId != null) {
            UploadSession knownSession = sessions.get(sessionId);
            if (knownSession != null) {
                if (!knownSession.datasetId().equals(datasetId)) {
                    throw new SocialNetworkDatasetContextException();
                }
                if (knownSession.isExpiredAt(clock.instant())) {
                    throw new SocialNetworkDatasetSessionExpiredException();
                }
            }
        }
        SocialNetworkDataset dataset = requireDataset(datasetId);
        if (!Objects.equals(dataset.datasetVersion(), datasetVersion)) {
            throw new SocialNetworkDatasetContextException();
        }
        if (!dataset.isUploadSession()) {
            if (sessionId != null) {
                throw new SocialNetworkDatasetContextException();
            }
            return dataset;
        }
        if (!Objects.equals(dataset.sessionId(), sessionId)) {
            throw new SocialNetworkDatasetContextException();
        }
        UploadSession session = session(dataset.sessionId());
        Instant now = clock.instant();
        if (session.isExpiredAt(now)) {
            throw new SocialNetworkDatasetSessionExpiredException();
        }
        touch(session.sessionId());
        return dataset;
    }

    public Instant sessionExpiresAt(String sessionId) {
        UploadSession session = session(sessionId);
        return session.idleExpiresAt().isBefore(session.hardExpiresAt())
                ? session.idleExpiresAt()
                : session.hardExpiresAt();
    }

    public SocialNetworkDataset selectDataset(String datasetId) {
        SocialNetworkDataset dataset = requireDataset(datasetId);
        if (dataset.isUploadSession() && session(dataset.sessionId()).isExpiredAt(clock.instant())) {
            throw new SocialNetworkDatasetSessionExpiredException();
        }
        currentDatasetId = dataset.datasetId();
        touch(dataset.sessionId());
        return dataset;
    }

    public void closeUploadSession(
            String datasetId, String datasetVersion, String sessionId) {
        UploadSession session = session(sessionId);
        String expectedVersion = "session-" + sessionId;
        if (!session.datasetId().equals(datasetId)
                || !expectedVersion.equals(datasetVersion)) {
            throw new SocialNetworkDatasetContextException();
        }
        closeUploadSession(sessionId);
    }

    public void closeUploadSession(String sessionId) {
        UploadSession session = session(sessionId);
        if (session.status() == UploadSession.SessionStatus.CLOSED) {
            return;
        }
        sessions.put(sessionId, new UploadSession(
                session.sessionId(),
                session.datasetId(),
                session.createdAt(),
                session.lastAccessedAt(),
                session.idleExpiresAt(),
                session.hardExpiresAt(),
                UploadSession.SessionStatus.CLOSED));
        datasets.remove(session.datasetId());
        if (session.datasetId().equals(currentDatasetId)) {
            currentDatasetId = BUILTIN_DATASET_ID;
        }
    }

    public int expireSessions() {
        Instant now = clock.instant();
        int expired = 0;
        for (UploadSession session : sessions.values()) {
            if (session.status() == UploadSession.SessionStatus.ACTIVE && session.isExpiredAt(now)) {
                sessions.put(session.sessionId(), new UploadSession(
                        session.sessionId(),
                        session.datasetId(),
                        session.createdAt(),
                        session.lastAccessedAt(),
                        session.idleExpiresAt(),
                        session.hardExpiresAt(),
                        UploadSession.SessionStatus.EXPIRED));
                datasets.remove(session.datasetId());
                if (session.datasetId().equals(currentDatasetId)) {
                    currentDatasetId = BUILTIN_DATASET_ID;
                }
                expired++;
            }
        }
        return expired;
    }

    private void touch(String sessionId) {
        if (sessionId == null) {
            return;
        }
        UploadSession session = session(sessionId);
        Instant now = clock.instant();
        sessions.put(sessionId, new UploadSession(
                session.sessionId(),
                session.datasetId(),
                session.createdAt(),
                now,
                now.plus(IDLE_TTL),
                session.hardExpiresAt(),
                session.status()));
    }

    private UploadSession session(String sessionId) {
        UploadSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("unknown upload session: " + sessionId);
        }
        return session;
    }

    private SocialNetworkDataset requireDataset(String datasetId) {
        SocialNetworkDataset dataset = datasets.get(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("unknown dataset: " + datasetId);
        }
        return dataset;
    }
}
