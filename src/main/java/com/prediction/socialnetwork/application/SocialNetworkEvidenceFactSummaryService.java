package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.cache.CacheResultType;
import com.prediction.socialnetwork.cache.SocialNetworkCacheContext;
import com.prediction.socialnetwork.cache.SocialNetworkCacheKey;
import com.prediction.socialnetwork.cache.SocialNetworkCacheKeyGenerator;
import com.prediction.socialnetwork.cache.SocialNetworkVersionContext;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class SocialNetworkEvidenceFactSummaryService {

    public static final String SUMMARY_ALGORITHM_VERSION = "evidence-facts-v1";
    private static final String ANALYSIS_SCOPE = "full_normalized_network";

    private final SocialNetworkEvidenceFactSummaryGenerator generator =
            new SocialNetworkEvidenceFactSummaryGenerator();
    private final SocialNetworkCacheKeyGenerator cacheKeyGenerator =
            new SocialNetworkCacheKeyGenerator();
    private final ConcurrentMap<String, SocialNetworkEvidenceFactSummaryGenerator.Summary>
            summaries = new ConcurrentHashMap<>();

    public SocialNetworkEvidenceFactSummaryGenerator.Summary get(
            SocialNetworkWorkspaceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.dataset().type() != DatasetType.BUILTIN) {
            throw new SocialNetworkEvidenceSummaryDatasetNotSupportedException();
        }

        SocialNetworkCacheKey cacheKey = cacheKey(snapshot);
        return summaries.computeIfAbsent(
                cacheKey.value(),
                ignored -> generator.generate(snapshot));
    }

    private SocialNetworkCacheKey cacheKey(SocialNetworkWorkspaceSnapshot snapshot) {
        SocialNetworkDataset dataset = snapshot.dataset();
        var metrics = snapshot.network().metrics();
        SocialNetworkVersionContext versions = new SocialNetworkVersionContext(
                dataset.datasetVersion(),
                dataset.normalizationVersion(),
                metrics.metricVersion(),
                metrics.pagerankVersion(),
                dataset.textProcessingVersion(),
                SocialNetworkVersionContext.CURRENT_STOPWORDS_VERSION,
                SocialNetworkVersionContext.CURRENT_TOKENIZER_VERSION,
                SocialNetworkVersionContext.CURRENT_TFIDF_VERSION,
                dataset.explanationVersion());
        SocialNetworkCacheContext context = SocialNetworkCacheContext.builtin(
                dataset.datasetId(),
                versions,
                CacheResultType.NETWORK_EVIDENCE_SUMMARY,
                Map.of(
                        "analysisScope", ANALYSIS_SCOPE,
                        "structuralRoleAlgorithmVersion",
                        snapshot.network().structuralRoles().algorithmVersion()),
                Map.of("summaryAlgorithmVersion", SUMMARY_ALGORITHM_VERSION));
        return cacheKeyGenerator.generate(context);
    }
}
