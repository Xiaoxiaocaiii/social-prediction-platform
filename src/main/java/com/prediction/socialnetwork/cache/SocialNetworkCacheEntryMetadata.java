package com.prediction.socialnetwork.cache;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SocialNetworkCacheEntryMetadata(
        SocialNetworkCacheKey cacheKey,
        SocialNetworkCacheContext context,
        Instant createdAt,
        Instant lastAccessedAt,
        Instant expiresAt,
        String qualityStatus,
        String capabilityStatus,
        Map<String, Integer> sampleCounts,
        Instant calculatedAt) {

    public SocialNetworkCacheEntryMetadata {
        cacheKey = Objects.requireNonNull(cacheKey, "cacheKey");
        context = Objects.requireNonNull(context, "context");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        lastAccessedAt = Objects.requireNonNull(lastAccessedAt, "lastAccessedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        qualityStatus = requireText(qualityStatus, "qualityStatus");
        capabilityStatus = requireText(capabilityStatus, "capabilityStatus");
        sampleCounts = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(sampleCounts, "sampleCounts")));
        calculatedAt = Objects.requireNonNull(calculatedAt, "calculatedAt");
    }

    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(expiresAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
