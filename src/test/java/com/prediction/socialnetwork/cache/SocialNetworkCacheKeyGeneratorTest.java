package com.prediction.socialnetwork.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.domain.DatasetType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialNetworkCacheKeyGeneratorTest {

    private final SocialNetworkCacheKeyGenerator generator = new SocialNetworkCacheKeyGenerator();
    private final SocialNetworkVersionContext versions = new SocialNetworkVersionContext(
            "dataset-v1.0",
            "normalization-v1.0",
            "network-metrics-v1.0",
            "pagerank-v1.0",
            "text-processing-v1.0",
            "stopwords-v1.0",
            "tokenizer-v1.0",
            "tfidf-v1.0",
            "explanation-v1.0");

    @Test
    void equivalentMapOrderProducesTheSameKey() {
        var firstScope = new LinkedHashMap<String, Object>();
        firstScope.put("userId", "10000000000000000001");
        firstScope.put("includeExternalNodes", true);
        var secondScope = new LinkedHashMap<String, Object>();
        secondScope.put("includeExternalNodes", true);
        secondScope.put("userId", "10000000000000000001");

        var first = context(firstScope, Map.of("topK", 10));
        var second = context(secondScope, Map.of("topK", 10));

        assertEquals(generator.generate(first).value(), generator.generate(second).value());
        assertEquals(64, generator.generate(first).value().length());
        assertTrue(generator.generate(first).canonicalMaterial().contains("10000000000000000001"));
    }

    @Test
    void versionResultTypeAndParameterChangesInvalidateKey() {
        var base = context(Map.of("from", "2025-01-01"), Map.of("topK", 10));
        var changedParameter = context(Map.of("from", "2025-01-01"), Map.of("topK", 20));
        var changedType = new SocialNetworkCacheContext(
                SocialNetworkCacheContext.NAMESPACE,
                "dataset",
                DatasetType.BUILTIN,
                null,
                versions,
                CacheResultType.QUALITY_REPORT,
                Map.of("from", "2025-01-01"),
                Map.of("topK", 10));
        var changedVersion = context(
                Map.of("from", "2025-01-01"),
                Map.of("topK", 10),
                new SocialNetworkVersionContext(
                        "dataset-v1.1",
                        versions.normalizationVersion(),
                        versions.metricVersion(),
                        versions.pagerankVersion(),
                        versions.textProcessingVersion(),
                        versions.stopwordsVersion(),
                        versions.tokenizerVersion(),
                        versions.tfidfVersion(),
                        versions.explanationVersion()));

        String baseKey = generator.generate(base).value();
        assertNotEquals(baseKey, generator.generate(changedParameter).value());
        assertNotEquals(baseKey, generator.generate(changedType).value());
        assertNotEquals(baseKey, generator.generate(changedVersion).value());
    }

    @Test
    void uploadSessionsNeverShareKeysWithBuiltinOrAnotherSession() {
        var builtin = context(Map.of(), Map.of());
        var uploadA = SocialNetworkCacheContext.uploadSession(
                "dataset", "session-a", versions, CacheResultType.QUALITY_REPORT, Map.of(), Map.of());
        var uploadB = SocialNetworkCacheContext.uploadSession(
                "dataset", "session-b", versions, CacheResultType.QUALITY_REPORT, Map.of(), Map.of());

        assertNotEquals(generator.generate(builtin).value(), generator.generate(uploadA).value());
        assertNotEquals(generator.generate(uploadA).value(), generator.generate(uploadB).value());
    }

    @Test
    void nullScopeValuesRemainPartOfCanonicalContext() {
        var scope = new LinkedHashMap<String, Object>();
        scope.put("from", null);
        scope.put("to", "2025-12-31");
        var context = context(scope, Map.of());
        String material = generator.generate(context).canonicalMaterial();

        assertTrue(material.contains("from"));
        assertTrue(material.contains("null"));
    }

    @Test
    void reusePolicyRejectsMismatchExpiredAndUnavailableEntries() {
        var context = context(Map.of("userId", "1"), Map.of());
        var key = generator.generate(context);
        Instant now = Instant.parse("2026-10-04T12:00:00Z");
        var entry = new SocialNetworkCacheEntryMetadata(
                key,
                context,
                now.minusSeconds(60),
                now.minusSeconds(10),
                now.plusSeconds(3600),
                "available_with_warnings",
                "available",
                Map.of("sample", 1),
                now.minusSeconds(60));
        var policy = new SocialNetworkCacheReusePolicy(generator);

        assertTrue(policy.evaluate(entry, context, now).reusable());
        assertEquals(
                "CACHE_KEY_MISMATCH",
                policy.evaluate(entry, context(Map.of("userId", "2"), Map.of()), now).reasonCode());
        assertEquals(
                "CACHE_EXPIRED",
                policy.evaluate(entry, context, now.plusSeconds(3601)).reasonCode());

        var unavailable = new SocialNetworkCacheEntryMetadata(
                key,
                context,
                now.minusSeconds(60),
                now.minusSeconds(10),
                now.plusSeconds(3600),
                "unavailable",
                "unavailable",
                Map.of(),
                now.minusSeconds(60));
        assertEquals(
                "CACHE_RESULT_UNAVAILABLE",
                policy.evaluate(unavailable, context, now).reasonCode());
    }

    private SocialNetworkCacheContext context(
            Map<String, Object> scope,
            Map<String, Object> parameters) {
        return context(scope, parameters, versions);
    }

    private SocialNetworkCacheContext context(
            Map<String, Object> scope,
            Map<String, Object> parameters,
            SocialNetworkVersionContext versionContext) {
        return SocialNetworkCacheContext.builtin(
                "dataset", versionContext, CacheResultType.NETWORK_SUMMARY, scope, parameters);
    }
}
