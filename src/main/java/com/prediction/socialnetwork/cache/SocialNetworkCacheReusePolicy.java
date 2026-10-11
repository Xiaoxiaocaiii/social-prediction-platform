package com.prediction.socialnetwork.cache;

import java.time.Instant;
import java.util.Objects;

public class SocialNetworkCacheReusePolicy {

    private final SocialNetworkCacheKeyGenerator keyGenerator;

    public SocialNetworkCacheReusePolicy() {
        this(new SocialNetworkCacheKeyGenerator());
    }

    public SocialNetworkCacheReusePolicy(SocialNetworkCacheKeyGenerator keyGenerator) {
        this.keyGenerator = Objects.requireNonNull(keyGenerator, "keyGenerator");
    }

    public CacheReuseDecision evaluate(
            SocialNetworkCacheEntryMetadata entry,
            SocialNetworkCacheContext requestedContext,
            Instant now) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(requestedContext, "requestedContext");
        Objects.requireNonNull(now, "now");

        String expectedKey = keyGenerator.generate(requestedContext).value();
        if (!entry.cacheKey().value().equals(expectedKey)) {
            return new CacheReuseDecision(
                    false, "CACHE_KEY_MISMATCH", "数据集、版本、范围或参数已变化，不能复用旧结果。");
        }
        if (entry.isExpiredAt(now)) {
            return new CacheReuseDecision(
                    false, "CACHE_EXPIRED", "缓存已过期，需要重新计算当前结果。");
        }
        if ("unavailable".equalsIgnoreCase(entry.qualityStatus())
                || "unavailable".equalsIgnoreCase(entry.capabilityStatus())) {
            return new CacheReuseDecision(
                    false, "CACHE_RESULT_UNAVAILABLE", "当前结果不可用，不能作为正常缓存命中返回。");
        }
        return new CacheReuseDecision(true, "CACHE_HIT", "缓存版本、范围和参数一致，可以复用结果。");
    }
}
