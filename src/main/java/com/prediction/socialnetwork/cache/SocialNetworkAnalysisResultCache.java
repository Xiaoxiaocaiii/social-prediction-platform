package com.prediction.socialnetwork.cache;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public final class SocialNetworkAnalysisResultCache {

    private final Map<CacheResultType, Entry> latestByAnalysis = new EnumMap<>(
            CacheResultType.class);

    public synchronized <T> Optional<Hit<T>> find(
            CacheResultType resultType,
            SocialNetworkCacheKey cacheKey,
            Class<T> resultClass) {
        requireAnalysisType(resultType);
        Objects.requireNonNull(cacheKey, "cacheKey");
        Objects.requireNonNull(resultClass, "resultClass");
        Entry entry = latestByAnalysis.get(resultType);
        if (entry == null || !entry.cacheKey().value().equals(cacheKey.value())) {
            return Optional.empty();
        }
        if (!resultClass.isInstance(entry.result())) {
            latestByAnalysis.remove(resultType);
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
        requireAnalysisType(resultType);
        latestByAnalysis.put(
                resultType,
                new Entry(
                        Objects.requireNonNull(cacheKey, "cacheKey"),
                        Objects.requireNonNull(result, "result"),
                        Objects.requireNonNull(cachedAt, "cachedAt")));
    }

    public synchronized void remove(
            CacheResultType resultType, SocialNetworkCacheKey cacheKey) {
        requireAnalysisType(resultType);
        Objects.requireNonNull(cacheKey, "cacheKey");
        Entry entry = latestByAnalysis.get(resultType);
        if (entry != null && entry.cacheKey().value().equals(cacheKey.value())) {
            latestByAnalysis.remove(resultType);
        }
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

    private record Entry(
            SocialNetworkCacheKey cacheKey,
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
