package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.capability.SocialNetworkCapabilityMatrix;
import com.prediction.socialnetwork.capability.SocialNetworkCapabilityMatrixGenerator;
import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.normalization.SocialNetworkNormalizationResult;
import com.prediction.socialnetwork.normalization.SocialNetworkNormalizer;
import com.prediction.socialnetwork.normalization.NormalizedTweet;
import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.network.SocialNetworkCalculator;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.quality.SocialNetworkQualityReport;
import com.prediction.socialnetwork.quality.SocialNetworkQualityReportGenerator;
import com.prediction.socialnetwork.reader.FileReadResult;
import com.prediction.socialnetwork.reader.SocialNetworkFileReader;
import com.prediction.socialnetwork.text.SocialNetworkTextAnalysis;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class SocialNetworkWorkspaceService {

    private final SocialNetworkDatasetRegistry registry;
    private final SocialNetworkFileReader fileReader;
    private final SocialNetworkNormalizer normalizer;
    private final SocialNetworkQualityReportGenerator qualityGenerator;
    private final SocialNetworkCapabilityMatrixGenerator capabilityGenerator;
    private final SocialNetworkCalculator networkCalculator;
    private volatile String cachedBuiltinDatasetKey;
    private volatile SocialNetworkWorkspaceSnapshot cachedBuiltinSnapshot;

    public SocialNetworkWorkspaceService(SocialNetworkDatasetRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.fileReader = new SocialNetworkFileReader();
        this.normalizer = new SocialNetworkNormalizer();
        this.qualityGenerator = new SocialNetworkQualityReportGenerator();
        this.capabilityGenerator = new SocialNetworkCapabilityMatrixGenerator();
        this.networkCalculator = new SocialNetworkCalculator();
    }

    public SocialNetworkWorkspaceSnapshot current() {
        return get(registry.currentDataset().datasetId());
    }

    public SocialNetworkWorkspaceSnapshot get(String datasetId) {
        SocialNetworkDataset dataset = registry.findDataset(datasetId);
        if (dataset.isUploadSession()) {
            throw new IllegalStateException("upload dataset analysis is not connected to the page shell yet");
        }
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
            return snapshot;
        }
    }

    private SocialNetworkWorkspaceSnapshot loadBuiltin(SocialNetworkDataset dataset) {
        List<FileReadResult> results = new ArrayList<>();
        for (DatasetFileDescriptor descriptor : dataset.files()) {
            Path path = Path.of(descriptor.logicalFileName());
            results.add(fileReader.read(path, descriptor, true));
        }

        List<com.prediction.socialnetwork.reader.RawRecord> users = records(results, 0);
        List<com.prediction.socialnetwork.reader.RawRecord> relationships = records(results, 1);
        List<com.prediction.socialnetwork.reader.RawRecord> tweets = records(results, 2);
        SocialNetworkNormalizationResult normalized =
                normalizer.normalize(users, relationships, tweets);
        SocialNetworkQualityReport quality = qualityGenerator.generate(
                dataset.datasetId(),
                dataset.datasetVersion(),
                dataset.type(),
                results.stream().map(FileReadResult::report).toList(),
                normalized);
        SocialNetworkCapabilityMatrix capabilities =
                capabilityGenerator.generate(dataset, quality);
        SocialNetworkGraphResult network = networkCalculator.calculate(
                normalized.users(), normalized.relationships());
        SocialNetworkTextAnalysis.Result textAnalysis =
                new SocialNetworkTextAnalysis().analyze(normalized.tweets());
        return new SocialNetworkWorkspaceSnapshot(
                dataset,
                quality,
                capabilities,
                network,
                interactionScale(normalized.tweets()),
                textAnalysis,
                normalized.users(),
                normalized.relationships(),
                normalized.tweets());
    }

    private static List<com.prediction.socialnetwork.reader.RawRecord> records(
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
