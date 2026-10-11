package com.prediction.socialnetwork.normalization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record NormalizedTweet(
        String sourceFileName,
        int sourceRowNumber,
        String tweetId,
        String authorUserId,
        String sourceFolder,
        Map<String, String> fields,
        AssociationStatus authorStatus,
        DedupStatus dedupStatus,
        NormalizationStatus normalizationStatus,
        boolean analysisIncluded,
        Set<String> qualityFlags) {

    public NormalizedTweet {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        qualityFlags = Set.copyOf(qualityFlags);
    }
}
