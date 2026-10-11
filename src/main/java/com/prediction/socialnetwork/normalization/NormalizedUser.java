package com.prediction.socialnetwork.normalization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record NormalizedUser(
        String sourceFileName,
        int sourceRowNumber,
        String userId,
        Map<String, String> fields,
        NormalizationStatus normalizationStatus,
        Set<String> qualityFlags) {

    public NormalizedUser {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        qualityFlags = Set.copyOf(qualityFlags);
    }
}
