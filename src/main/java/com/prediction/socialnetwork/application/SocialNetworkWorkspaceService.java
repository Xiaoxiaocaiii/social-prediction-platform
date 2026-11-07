package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.capability.SocialNetworkCapabilityMatrix;
import com.prediction.socialnetwork.capability.SocialNetworkCapabilityMatrixGenerator;
import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.DatasetStatus;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.normalization.SocialNetworkNormalizationResult;
import com.prediction.socialnetwork.normalization.SocialNetworkNormalizer;
import com.prediction.socialnetwork.normalization.NormalizedTweet;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.isolation.WindowsResourcePreflight;
import com.prediction.socialnetwork.network.SocialNetworkCalculator;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.quality.SocialNetworkQualityReport;
import com.prediction.socialnetwork.quality.SocialNetworkQualityReportGenerator;
import com.prediction.socialnetwork.reader.FileReadResult;
import com.prediction.socialnetwork.reader.FileReadReport;
import com.prediction.socialnetwork.reader.RawRecord;
import com.prediction.socialnetwork.reader.SocialNetworkFileReader;
import com.prediction.socialnetwork.text.SocialNetworkTextAnalysis;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SocialNetworkWorkspaceService {

    private final SocialNetworkDatasetRegistry registry;
    private final SocialNetworkFileReader fileReader;
    private final SocialNetworkNormalizer normalizer;
    private final SocialNetworkQualityReportGenerator qualityGenerator;
    private final SocialNetworkCapabilityMatrixGenerator capabilityGenerator;
    private final SocialNetworkCalculator networkCalculator;
    private final SocialNetworkAnalysisResultStore resultStore;
    private final Map<String, SocialNetworkWorkspaceSnapshot> uploadSnapshots =
            new ConcurrentHashMap<>();
    private volatile String cachedBuiltinDatasetKey;
    private volatile SocialNetworkWorkspaceSnapshot cachedBuiltinSnapshot;
    private volatile String cachedBuiltinGraphFingerprint;
    private volatile String cachedAnalysisGraphKey;
    private volatile String cachedAnalysisGraphFingerprint;
    private volatile SocialNetworkGraphResult cachedAnalysisGraph;
    private volatile String cachedGroupEvidenceKey;
    private volatile String cachedGroupEvidenceFingerprint;
    private volatile GroupEvidenceInput cachedGroupEvidence;

    public SocialNetworkWorkspaceService(SocialNetworkDatasetRegistry registry) {
        this(registry, SocialNetworkAnalysisResultStore.disabled());
    }

    @Autowired
    public SocialNetworkWorkspaceService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkAnalysisResultStore resultStore) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.fileReader = new SocialNetworkFileReader();
        this.normalizer = new SocialNetworkNormalizer();
        this.qualityGenerator = new SocialNetworkQualityReportGenerator();
        this.capabilityGenerator = new SocialNetworkCapabilityMatrixGenerator();
        this.networkCalculator = new SocialNetworkCalculator();
        this.resultStore = Objects.requireNonNull(resultStore, "resultStore");
    }

    public SocialNetworkWorkspaceSnapshot current() {
        return get(registry.currentDataset().datasetId());
    }

    public SocialNetworkWorkspaceSnapshot get(String datasetId) {
        SocialNetworkDataset dataset = registry.findDataset(datasetId);
        if (dataset.isUploadSession()) {
            throw new SocialNetworkDatasetContextException();
        }
        return getBuiltinSnapshot(dataset);
    }

    public SocialNetworkWorkspaceSnapshot getForAnalysis(
            String datasetId, String expectedGraphFingerprint) {
        if (expectedGraphFingerprint == null || expectedGraphFingerprint.isBlank()) {
            throw new IllegalArgumentException("expectedGraphFingerprint must not be blank");
        }
        SocialNetworkDataset dataset = registry.findDataset(datasetId);
        if (dataset.isUploadSession()) {
            throw new SocialNetworkDatasetContextException();
        }
        String cacheKey = dataset.datasetId() + "|" + dataset.datasetVersion();
        synchronized (this) {
            SocialNetworkWorkspaceSnapshot snapshot = cachedBuiltinSnapshot;
            if (cacheKey.equals(cachedBuiltinDatasetKey)
                    && expectedGraphFingerprint.equals(cachedBuiltinGraphFingerprint)
                    && snapshot != null) {
                return snapshot;
            }
            snapshot = loadBuiltin(dataset);
            cachedBuiltinDatasetKey = cacheKey;
            cachedBuiltinSnapshot = snapshot;
            cachedBuiltinGraphFingerprint = expectedGraphFingerprint;
            return snapshot;
        }
    }

    public SocialNetworkGraphResult getNetworkForAnalysis(
            String datasetId, String expectedGraphFingerprint) {
        if (expectedGraphFingerprint == null || expectedGraphFingerprint.isBlank()) {
            throw new IllegalArgumentException("expectedGraphFingerprint must not be blank");
        }
        SocialNetworkDataset dataset = registry.findDataset(datasetId);
        if (dataset.isUploadSession()) {
            throw new SocialNetworkDatasetContextException();
        }
        String cacheKey = dataset.datasetId() + "|" + dataset.datasetVersion();
        SocialNetworkGraphResult graph = cachedAnalysisGraph;
        if (cacheKey.equals(cachedAnalysisGraphKey)
                && expectedGraphFingerprint.equals(cachedAnalysisGraphFingerprint)
                && graph != null) {
            return graph;
        }
        synchronized (this) {
            graph = cachedAnalysisGraph;
            if (cacheKey.equals(cachedAnalysisGraphKey)
                    && expectedGraphFingerprint.equals(cachedAnalysisGraphFingerprint)
                    && graph != null) {
                return graph;
            }
            List<RawRecord> users = readBuiltinRecords(dataset, DatasetFileRole.USERS);
            List<RawRecord> relationships =
                    readBuiltinRecords(dataset, DatasetFileRole.RELATIONSHIPS);
            SocialNetworkNormalizationResult normalized =
                    normalizer.normalize(users, relationships, List.of());
            graph = networkCalculator.calculateAnalysisInput(
                    normalized.users(), normalized.relationships());
            GroupEvidenceInput evidenceInput = new GroupEvidenceInput(
                    graph, normalized.users(), normalized.relationships());
            cachedAnalysisGraphKey = cacheKey;
            cachedAnalysisGraphFingerprint = expectedGraphFingerprint;
            cachedAnalysisGraph = graph;
            cacheGroupEvidence(cacheKey, expectedGraphFingerprint, evidenceInput);
            resultStore.saveGroupEvidenceInput(
                    dataset.datasetId(),
                    dataset.datasetVersion(),
                    expectedGraphFingerprint,
                    evidenceInput);
            return graph;
        }
    }

    public Optional<SocialNetworkGraphResult> cachedNetworkForAnalysis(
            String datasetId, String expectedGraphFingerprint) {
        SocialNetworkDataset dataset = registry.findDataset(datasetId);
        String key = dataset.datasetId() + "|" + dataset.datasetVersion();
        synchronized (this) {
            if (key.equals(cachedAnalysisGraphKey)
                    && Objects.equals(expectedGraphFingerprint, cachedAnalysisGraphFingerprint)) {
                return Optional.ofNullable(cachedAnalysisGraph);
            }
            return Optional.empty();
        }
    }

    public Map<String, String> analysisProfileNames(String datasetId) {
        SocialNetworkDataset dataset = registry.findDataset(datasetId);
        if (dataset.isUploadSession()) {
            throw new SocialNetworkDatasetContextException();
        }
        List<RawRecord> users = readBuiltinRecords(dataset, DatasetFileRole.USERS);
        SocialNetworkNormalizationResult normalized =
                normalizer.normalize(users, List.of(), List.of());
        Map<String, String> names = new java.util.LinkedHashMap<>();
        normalized.users().forEach(user -> {
            if (user.userId() != null && !user.userId().isBlank()) {
                String username = user.fields().getOrDefault("username", "");
                if (!username.isBlank()) {
                    names.putIfAbsent(user.userId(), username);
                }
            }
        });
        return Map.copyOf(names);
    }

    public GroupEvidenceInput getGroupEvidenceInput(
            String datasetId, String expectedGraphFingerprint) {
        if (expectedGraphFingerprint == null || expectedGraphFingerprint.isBlank()) {
            throw new IllegalArgumentException("expectedGraphFingerprint must not be blank");
        }
        SocialNetworkDataset dataset = registry.findDataset(datasetId);
        if (dataset.isUploadSession()) {
            throw new SocialNetworkDatasetContextException();
        }
        String key = dataset.datasetId() + "|" + dataset.datasetVersion();
        synchronized (this) {
            if (key.equals(cachedGroupEvidenceKey)
                    && expectedGraphFingerprint.equals(cachedGroupEvidenceFingerprint)
                    && cachedGroupEvidence != null) {
                return cachedGroupEvidence;
            }
            Optional<GroupEvidenceInput> persisted = resultStore.loadGroupEvidenceInput(
                    dataset.datasetId(),
                    dataset.datasetVersion(),
                    expectedGraphFingerprint);
            if (persisted.isPresent()) {
                GroupEvidenceInput input = persisted.get();
                cachedAnalysisGraphKey = key;
                cachedAnalysisGraphFingerprint = expectedGraphFingerprint;
                cachedAnalysisGraph = input.network();
                cacheGroupEvidence(key, expectedGraphFingerprint, input);
                return input;
            }
            WindowsResourcePreflight.requireMinimumAvailableMemory();
            List<RawRecord> users = readBuiltinRecords(dataset, DatasetFileRole.USERS);
            List<RawRecord> relationships =
                    readBuiltinRecords(dataset, DatasetFileRole.RELATIONSHIPS);
            SocialNetworkNormalizationResult normalized =
                    normalizer.normalize(users, relationships, List.of());
            SocialNetworkGraphResult graph = cachedNetworkForAnalysis(
                    datasetId, expectedGraphFingerprint)
                    .orElseGet(() -> networkCalculator.calculateAnalysisInput(
                            normalized.users(), normalized.relationships()));
            GroupEvidenceInput input = new GroupEvidenceInput(
                    graph, normalized.users(), normalized.relationships());
            cacheGroupEvidence(key, expectedGraphFingerprint, input);
            resultStore.saveGroupEvidenceInput(
                    dataset.datasetId(),
                    dataset.datasetVersion(),
                    expectedGraphFingerprint,
                    input);
            return input;
        }
    }

    private void cacheGroupEvidence(
            String key, String fingerprint, GroupEvidenceInput input) {
        cachedGroupEvidenceKey = key;
        cachedGroupEvidenceFingerprint = fingerprint;
        cachedGroupEvidence = input;
    }

    public record GroupEvidenceInput(
            SocialNetworkGraphResult network,
            List<NormalizedUser> users,
            List<NormalizedRelationship> relationships) {
        public GroupEvidenceInput {
            users = List.copyOf(users);
            relationships = List.copyOf(relationships);
        }
    }

    public SocialNetworkWorkspaceSnapshot get(
            String datasetId, String datasetVersion, String sessionId) {
        SocialNetworkDataset dataset =
                registry.resolveDatasetContext(datasetId, datasetVersion, sessionId);
        if (!dataset.isUploadSession()) {
            return getBuiltinSnapshot(dataset);
        }
        SocialNetworkWorkspaceSnapshot snapshot = uploadSnapshots.get(sessionId);
        if (snapshot == null
                || !snapshot.dataset().datasetId().equals(datasetId)
                || !snapshot.dataset().datasetVersion().equals(datasetVersion)) {
            throw new IllegalStateException("upload dataset snapshot is unavailable");
        }
        return snapshot;
    }

    public SocialNetworkWorkspaceSnapshot prepareUploadSnapshot(
            SocialNetworkDataset candidate,
            List<RawRecord> users,
            List<RawRecord> relationships,
            List<RawRecord> tweets,
            List<FileReadReport> fileReports) {
        Objects.requireNonNull(candidate, "candidate");
        if (!candidate.isUploadSession()) {
            throw new IllegalArgumentException("candidate must be an upload dataset");
        }
        return buildSnapshot(candidate, users, relationships, tweets, fileReports, true);
    }

    public void registerUploadSnapshot(SocialNetworkWorkspaceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        SocialNetworkDataset dataset = snapshot.dataset();
        if (!dataset.isUploadSession()) {
            throw new IllegalArgumentException("snapshot must belong to an upload session");
        }
        SocialNetworkWorkspaceSnapshot existing =
                uploadSnapshots.putIfAbsent(dataset.sessionId(), snapshot);
        if (existing != null) {
            throw new IllegalStateException("upload snapshot already exists");
        }
    }

    public void removeUploadSnapshot(String sessionId) {
        if (sessionId != null) {
            uploadSnapshots.remove(sessionId);
        }
    }

    private SocialNetworkWorkspaceSnapshot getBuiltinSnapshot(SocialNetworkDataset dataset) {
        String cacheKey = dataset.datasetId() + "|" + dataset.datasetVersion();
        SocialNetworkWorkspaceSnapshot snapshot = cachedBuiltinSnapshot;
        if (cacheKey.equals(cachedBuiltinDatasetKey) && snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            snapshot = cachedBuiltinSnapshot;
            if (cacheKey.equals(cachedBuiltinDatasetKey) && snapshot != null) {
                return snapshot;
            }
            snapshot = loadBuiltin(dataset);
            cachedBuiltinDatasetKey = cacheKey;
            cachedBuiltinSnapshot = snapshot;
            cachedBuiltinGraphFingerprint = null;
            return snapshot;
        }
    }

    private SocialNetworkWorkspaceSnapshot loadBuiltin(SocialNetworkDataset dataset) {
        List<FileReadResult> results = new ArrayList<>();
        for (DatasetFileDescriptor descriptor : dataset.files()) {
            Path path = Path.of(descriptor.logicalFileName());
            results.add(fileReader.read(path, descriptor, true));
        }

        return buildSnapshot(
                dataset,
                records(results, 0),
                records(results, 1),
                records(results, 2),
                results.stream().map(FileReadResult::report).toList(),
                false);
    }

    private List<RawRecord> readBuiltinRecords(
            SocialNetworkDataset dataset, DatasetFileRole role) {
        DatasetFileDescriptor descriptor = dataset.files().stream()
                .filter(file -> file.role() == role)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Built-in dataset is missing file role " + role));
        return fileReader.read(Path.of(descriptor.logicalFileName()), descriptor, true).records();
    }

    private SocialNetworkWorkspaceSnapshot buildSnapshot(
            SocialNetworkDataset dataset,
            List<RawRecord> users,
            List<RawRecord> relationships,
            List<RawRecord> tweets,
            List<FileReadReport> fileReports,
            boolean useQualityStatus) {
        SocialNetworkNormalizationResult normalized =
                normalizer.normalize(users, relationships, tweets);
        SocialNetworkQualityReport quality = qualityGenerator.generate(
                dataset.datasetId(),
                dataset.datasetVersion(),
                dataset.type(),
                fileReports,
                normalized);
        SocialNetworkDataset snapshotDataset = useQualityStatus
                ? withStatus(dataset, quality.datasetStatus())
                : dataset;
        SocialNetworkCapabilityMatrix capabilities =
                capabilityGenerator.generate(snapshotDataset, quality);
        SocialNetworkGraphResult network = networkCalculator.calculateWithoutCommunities(
                normalized.users(), normalized.relationships());
        SocialNetworkInteractionScale interactions = interactionScale(normalized.tweets());
        return new SocialNetworkWorkspaceSnapshot(
                snapshotDataset,
                quality,
                capabilities,
                network,
                interactions,
                () -> new SocialNetworkTextAnalysis().analyze(normalized.tweets()),
                normalized.users(),
                normalized.relationships(),
                normalized.tweets());
    }

    private static SocialNetworkDataset withStatus(
            SocialNetworkDataset dataset, DatasetStatus status) {
        return new SocialNetworkDataset(
                dataset.datasetId(),
                dataset.name(),
                dataset.type(),
                dataset.datasetVersion(),
                status,
                dataset.description(),
                dataset.sourceDescription(),
                dataset.timeRangeFrom(),
                dataset.timeRangeTo(),
                dataset.files(),
                dataset.sessionId(),
                dataset.normalizationVersion(),
                dataset.metricVersion(),
                dataset.textProcessingVersion(),
                dataset.explanationVersion());
    }

    private static List<RawRecord> records(
            List<FileReadResult> results, int index) {
        return results.size() > index ? results.get(index).records() : List.of();
    }

    private static SocialNetworkInteractionScale interactionScale(List<NormalizedTweet> tweets) {
        Map<String, InteractionMetricSummary> metrics = new java.util.LinkedHashMap<>();
        int analysisSampleCount = (int) tweets.stream()
                .filter(NormalizedTweet::analysisIncluded)
                .count();
        int linkedAuthorSampleCount = (int) tweets.stream()
                .filter(tweet -> tweet.analysisIncluded()
                        && tweet.authorStatus() == AssociationStatus.LINKED)
                .count();
        int unassignedSourceSampleCount = (int) tweets.stream()
                .filter(tweet -> tweet.analysisIncluded()
                        && tweet.authorStatus() == AssociationStatus.UNASSIGNED_SOURCE)
                .count();
        int unlinkedAuthorSampleCount = (int) tweets.stream()
                .filter(tweet -> tweet.analysisIncluded()
                        && tweet.authorStatus() == AssociationStatus.UNLINKED_AUTHOR)
                .count();
        for (String field : List.of("like_count", "reply_count", "repost_count")) {
            long total = 0L;
            int valid = 0;
            int missing = 0;
            int zero = 0;
            List<Long> values = new ArrayList<>();
            for (NormalizedTweet tweet : tweets) {
                if (!tweet.analysisIncluded()) {
                    continue;
                }
                String raw = tweet.fields().get(field);
                if (raw == null || raw.isBlank()) {
                    missing++;
                    continue;
                }
                try {
                    long value = Long.parseLong(raw);
                    if (value < 0) {
                        missing++;
                        continue;
                    }
                    total += value;
                    valid++;
                    values.add(value);
                    if (value == 0) {
                        zero++;
                    }
                } catch (NumberFormatException exception) {
                    missing++;
                }
            }
            metrics.put(field, new InteractionMetricSummary(
                    field,
                    total,
                    valid == 0 ? 0.0 : total * 1.0 / valid,
                    median(values),
                    valid,
                    missing,
                    zero));
        }
        return new SocialNetworkInteractionScale(
                analysisSampleCount > 0,
                analysisSampleCount == 0 ? "缺少可用于分析的去重推文样本。" : null,
                metrics,
                analysisSampleCount,
                linkedAuthorSampleCount,
                unassignedSourceSampleCount,
                unlinkedAuthorSampleCount);
    }

    private static double median(List<Long> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        values.sort(Comparator.naturalOrder());
        int middle = values.size() / 2;
        if (values.size() % 2 == 1) {
            return values.get(middle);
        }
        return (values.get(middle - 1) + values.get(middle)) / 2.0;
    }
}
