package com.prediction.socialnetwork.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.application.SocialNetworkDataFingerprintService;
import com.prediction.socialnetwork.application.SocialNetworkDatasetRegistry;
import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.SourceFormat;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SocialNetworkAnalysisResultCacheTest {

    private final SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
    private final SocialNetworkAnalysisCacheKeyFactory keyFactory =
            new SocialNetworkAnalysisCacheKeyFactory();

    @Test
    void keyIncludesInputFingerprintAlgorithmContractAndProjectionParameters() {
        var dataset = registry.findDataset(SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
        var firstFingerprint = fingerprint("input-a");
        var secondFingerprint = fingerprint("input-b");

        var firstKey = keyFactory.clique(dataset, firstFingerprint).cacheKey();
        assertEquals(firstKey, keyFactory.clique(dataset, firstFingerprint).cacheKey());
        assertFalse(firstKey.value().equals(
                keyFactory.clique(dataset, secondFingerprint).cacheKey().value()));

        var baselineIdentity = keyFactory.louvain(
                dataset, firstFingerprint, LouvainAnalysisParameters.DEFAULT);
        var baselineKey = baselineIdentity.cacheKey();
        assertEquals("unweighted", baselineIdentity.parameters().get("weighting"));
        assertEquals("not_applicable", baselineIdentity.parameters().get("randomSeed"));
        var reciprocalOnly = new LouvainAnalysisParameters(
                1.0,
                LouvainAnalysisParameters.NodeOrder.DEGREE_DESCENDING_THEN_ID_ASCENDING,
                LouvainAnalysisParameters.RelationProjection.MUTUAL_ONLY);
        var variantKey = keyFactory.louvain(
                dataset, firstFingerprint, reciprocalOnly).cacheKey();
        assertFalse(baselineKey.value().equals(variantKey.value()));
        assertFalse(baselineKey.value().equals(firstKey.value()));

        var resolutionVariant = new LouvainAnalysisParameters(
                0.5,
                LouvainAnalysisParameters.NodeOrder.DEGREE_DESCENDING_THEN_ID_ASCENDING,
                LouvainAnalysisParameters.RelationProjection.ANY_DIRECTION);
        assertFalse(baselineKey.value().equals(keyFactory.louvain(
                        dataset, firstFingerprint, resolutionVariant).cacheKey().value()));
    }

    @Test
    void cacheSeparatesAlgorithmsAndRetainsLatestEntryPerSession() {
        SocialNetworkAnalysisResultCache cache = new SocialNetworkAnalysisResultCache();
        var dataset = registry.findDataset(SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
        var fingerprint = fingerprint("input-a");
        var cliqueKey = keyFactory.clique(dataset, fingerprint).cacheKey();
        var louvainKey = keyFactory.louvain(
                dataset, fingerprint, LouvainAnalysisParameters.DEFAULT).cacheKey();

        cache.put(CacheResultType.CLIQUE_ANALYSIS, cliqueKey, "partial-clique", Instant.EPOCH);
        cache.put(CacheResultType.LOUVAIN_ANALYSIS, louvainKey, "complete-louvain", Instant.EPOCH);

        assertEquals(2, cache.retainedAnalysisCount());
        assertEquals(
                "partial-clique",
                cache.find(CacheResultType.CLIQUE_ANALYSIS, cliqueKey, String.class)
                        .orElseThrow()
                        .result());
        assertTrue(cache.find(CacheResultType.CLIQUE_ANALYSIS, louvainKey, String.class).isEmpty());

        var changedFingerprintKey =
                keyFactory.clique(dataset, fingerprint("input-b")).cacheKey();
        assertTrue(cache.find(
                CacheResultType.CLIQUE_ANALYSIS, changedFingerprintKey, String.class).isEmpty());
        cache.put(
                CacheResultType.CLIQUE_ANALYSIS,
                changedFingerprintKey,
                "latest-clique",
                Instant.EPOCH);
        assertEquals(2, cache.retainedAnalysisCount());
        assertEquals(
                "latest-clique",
                cache.find(
                                CacheResultType.CLIQUE_ANALYSIS,
                                changedFingerprintKey,
                                String.class)
                        .orElseThrow()
                        .result());
        assertThrows(
                IllegalArgumentException.class,
                () -> cache.put(CacheResultType.EXPORT, cliqueKey, "not-analysis", Instant.EPOCH));
    }

    @Test
    void uploadIdentityBindsContentToSessionAndParameters() {
        var descriptor =
                new DatasetFileDescriptor("users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8");
        var firstDataset = registry.prepareUploadDataset(List.of(descriptor), null);
        var secondDataset = registry.prepareUploadDataset(List.of(descriptor), null);
        String sameContentFingerprint = "a".repeat(64);

        var first = keyFactory.louvain(
                firstDataset,
                fingerprint(firstDataset, sameContentFingerprint),
                LouvainAnalysisParameters.DEFAULT);
        var second = keyFactory.louvain(
                secondDataset,
                fingerprint(secondDataset, sameContentFingerprint),
                LouvainAnalysisParameters.DEFAULT);

        assertEquals(firstDataset.datasetId(), first.datasetId());
        assertEquals(firstDataset.datasetVersion(), first.datasetVersion());
        assertEquals(firstDataset.sessionId(), first.sessionId());
        assertNotEquals(first.cacheKey().value(), second.cacheKey().value());

        var parameterVariant = new LouvainAnalysisParameters(
                0.5,
                LouvainAnalysisParameters.NodeOrder.DEGREE_DESCENDING_THEN_ID_ASCENDING,
                LouvainAnalysisParameters.RelationProjection.ANY_DIRECTION);
        assertNotEquals(
                first.cacheKey().value(),
                keyFactory.louvain(
                                firstDataset,
                                fingerprint(firstDataset, sameContentFingerprint),
                                parameterVariant)
                        .cacheKey()
                        .value());
    }

    @Test
    void removingOneSessionCacheDoesNotRemoveAnotherSessionResult() {
        SocialNetworkAnalysisResultCache cache = new SocialNetworkAnalysisResultCache();
        var descriptor =
                new DatasetFileDescriptor("users.csv", DatasetFileRole.USERS, SourceFormat.CSV, "utf8");
        var firstDataset = registry.prepareUploadDataset(List.of(descriptor), null);
        var secondDataset = registry.prepareUploadDataset(List.of(descriptor), null);
        var firstIdentity = keyFactory.louvain(
                firstDataset,
                fingerprint(firstDataset, "b".repeat(64)),
                LouvainAnalysisParameters.DEFAULT);
        var secondIdentity = keyFactory.louvain(
                secondDataset,
                fingerprint(secondDataset, "b".repeat(64)),
                LouvainAnalysisParameters.DEFAULT);
        cache.put(
                CacheResultType.LOUVAIN_ANALYSIS,
                firstIdentity.cacheKey(),
                firstIdentity.sessionId(),
                "first-session-result",
                Instant.EPOCH);
        cache.put(
                CacheResultType.LOUVAIN_ANALYSIS,
                secondIdentity.cacheKey(),
                secondIdentity.sessionId(),
                "second-session-result",
                Instant.EPOCH);

        assertEquals(1, cache.removeSession(firstIdentity.sessionId()));
        assertEquals(
                "second-session-result",
                cache.find(
                                CacheResultType.LOUVAIN_ANALYSIS,
                                secondIdentity.cacheKey(),
                                String.class)
                        .orElseThrow()
                        .result());
        assertEquals(1, cache.removeSession(secondIdentity.sessionId()));
        assertTrue(cache.find(
                CacheResultType.LOUVAIN_ANALYSIS,
                secondIdentity.cacheKey(),
                String.class).isEmpty());
    }

    private static SocialNetworkDataFingerprintService.Fingerprint fingerprint(
            String dataFingerprint) {
        return new SocialNetworkDataFingerprintService.Fingerprint(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                "normalization-v1",
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                List.of(),
                dataFingerprint);
    }

    private static SocialNetworkDataFingerprintService.Fingerprint fingerprint(
            com.prediction.socialnetwork.domain.SocialNetworkDataset dataset,
            String dataFingerprint) {
        return new SocialNetworkDataFingerprintService.Fingerprint(
                dataset.datasetId(),
                dataset.datasetVersion(),
                dataset.normalizationVersion(),
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                List.of(),
                dataFingerprint);
    }
}
