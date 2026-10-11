package com.prediction.socialnetwork.cache;

import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import java.util.Objects;

public record SocialNetworkVersionContext(
        String datasetVersion,
        String normalizationVersion,
        String metricVersion,
        String pagerankVersion,
        String textProcessingVersion,
        String stopwordsVersion,
        String tokenizerVersion,
        String tfidfVersion,
        String explanationVersion) {

    public static final String CURRENT_PAGERANK_VERSION = "pagerank-v1.0";
    public static final String CURRENT_STOPWORDS_VERSION = "stopwords-v1.0";
    public static final String CURRENT_TOKENIZER_VERSION = "tokenizer-v1.0";
    public static final String CURRENT_TFIDF_VERSION = "tfidf-v1.0";

    public SocialNetworkVersionContext {
        datasetVersion = requireText(datasetVersion, "datasetVersion");
        normalizationVersion = requireText(normalizationVersion, "normalizationVersion");
        metricVersion = requireText(metricVersion, "metricVersion");
        pagerankVersion = requireText(pagerankVersion, "pagerankVersion");
        textProcessingVersion = requireText(textProcessingVersion, "textProcessingVersion");
        stopwordsVersion = requireText(stopwordsVersion, "stopwordsVersion");
        tokenizerVersion = requireText(tokenizerVersion, "tokenizerVersion");
        tfidfVersion = requireText(tfidfVersion, "tfidfVersion");
        explanationVersion = requireText(explanationVersion, "explanationVersion");
    }

    public static SocialNetworkVersionContext from(SocialNetworkDataset dataset) {
        Objects.requireNonNull(dataset, "dataset");
        return new SocialNetworkVersionContext(
                dataset.datasetVersion(),
                dataset.normalizationVersion(),
                dataset.metricVersion(),
                CURRENT_PAGERANK_VERSION,
                dataset.textProcessingVersion(),
                CURRENT_STOPWORDS_VERSION,
                CURRENT_TOKENIZER_VERSION,
                CURRENT_TFIDF_VERSION,
                dataset.explanationVersion());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
