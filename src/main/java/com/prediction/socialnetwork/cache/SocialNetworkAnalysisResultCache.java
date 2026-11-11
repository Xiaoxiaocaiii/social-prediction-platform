package com.prediction.socialnetwork.cache;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public final class SocialNetworkAnalysisResultCache {

    private final Map<CacheSlot, Entry> latestByAnalysis = new HashMap<>();

    public synchronized <T> Optional<Hit<T>> find(
            CacheResultType resultType,
            SocialNetworkCacheKey cacheKey,
            Class<T> resultClass) {
        requireAnalysisType(resultType);
        Objects.requireNonNull(cacheKey, "cacheKey");
        Objects.requireNonNull(resultClass, "resultClass");
        Map.Entry<CacheSlot, Entry> matching = latestByAnalysis.entrySet().stream()
                .filter(entry -> entry.getKey().resultType() == resultType)
                .filter(entry -> entry.getValue().cacheKey().value().equals(cacheKey.value()))
                .findFirst()
                .orElse(null);
        if (matching == null) {
            return Optional.empty();
        }
        Entry entry = matching.getValue();
        if (!resultClass.isInstance(entry.result())) {
            latestByAnalysis.remove(matching.getKey());
            return Optional.empty();
        }
        return Optional.of(new Hit<>(
                resultClass.cast(entry.result()),
                entry.cacheKey(),
                entry.cachedAt()));
    }

    public synchronized void put(
            CacheResultType resultType,
            SocialNetworkCacheKey cacheKey,
            Object result,
            Instant cachedAt) {
        put(resultType, cacheKey, null, result, cachedAt);
    }

    public synchronized void put(
            CacheResultType resultType,
            SocialNetworkCacheKey cacheKey,
            String sessionId,
            Object result,
            Instant cachedAt) {
        requireAnalysisType(resultType);
        if (sessionId != null && sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        CacheSlot slot = new CacheSlot(resultType, sessionId);
        latestByAnalysis.put(
                slot,
                new Entry(
                        Objects.requireNonNull(cacheKey, "cacheKey"),
                        sessionId,
                        Objects.requireNonNull(result, "result"),
                        Objects.requireNonNull(cachedAt, "cachedAt")));
    }

    public synchronized int removeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        int previousSize = latestByAnalysis.size();
        latestByAnalysis.keySet().removeIf(slot -> sessionId.equals(slot.sessionId()));
        return previousSize - latestByAnalysis.size();
    }

    public synchronized void remove(
            CacheResultType resultType, SocialNetworkCacheKey cacheKey) {
        requireAnalysisType(resultType);
        Objects.requireNonNull(cacheKey, "cacheKey");
        latestByAnalysis.entrySet().removeIf(entry ->
                entry.getKey().resultType() == resultType
                        && entry.getValue().cacheKey().value().equals(cacheKey.value()));
    }

    public synchronized int retainedAnalysisCount() {
        return latestByAnalysis.size();
    }

    private static void requireAnalysisType(CacheResultType resultType) {
        Objects.requireNonNull(resultType, "resultType");
        if (resultType != CacheResultType.CLIQUE_ANALYSIS
                && resultType != CacheResultType.LOUVAIN_ANALYSIS) {
            throw new IllegalArgumentException(
                    "Only Clique and Louvain analysis results use this bounded cache");
        }
    }

    private record CacheSlot(CacheResultType resultType, String sessionId) {
    }

    private record Entry(
            SocialNetworkCacheKey cacheKey,
            String sessionId,
            Object result,
            Instant cachedAt) {
    }

    public record Hit<T>(
            T result,
            SocialNetworkCacheKey cacheKey,
            Instant cachedAt) {

        public Hit {
            result = Objects.requireNonNull(result, "result");
            cacheKey = Objects.requireNonNull(cacheKey, "cacheKey");
            cachedAt = Objects.requireNonNull(cachedAt, "cachedAt");
        }
    }
}
