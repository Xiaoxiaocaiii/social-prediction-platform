package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.application.SocialNetworkDataFingerprintService.FileFingerprint;
import com.prediction.socialnetwork.network.LouvainQualitySummary;
import com.prediction.socialnetwork.network.CommunityDetectionResult;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkCommunity;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SocialNetworkAnalysisResultStoreTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void persistsOnlyCompleteCurrentResultsAndChecksAnalysisKey() {
        SocialNetworkAnalysisResultStore store =
                new SocialNetworkAnalysisResultStore(objectMapper, tempDir);
        CliqueEnumerationWorkflowView clique = cliqueView();
        LouvainAnalysisWorkflowView louvain = louvainView();

        store.saveClique(clique);
        store.saveLouvain(louvain);

        SocialNetworkAnalysisResultStore restartedStore =
                new SocialNetworkAnalysisResultStore(objectMapper, tempDir);
        assertEquals(clique, restartedStore.loadClique("clique-key").orElseThrow());
        assertEquals(louvain, restartedStore.loadLouvain("louvain-key").orElseThrow());
        assertTrue(restartedStore.loadClique("changed-clique-key").isEmpty());
        assertTrue(restartedStore.loadLouvain("changed-louvain-key").isEmpty());

        restartedStore.saveClique(new CliqueEnumerationWorkflowView(
                clique.datasetId(),
                clique.datasetVersion(),
                "TIME_LIMIT",
                false,
                "time_limit",
                clique.requestedAt(),
                clique.finishedAt(),
                clique.inputNodeCount(),
                clique.inputDirectedEdgeCount(),
                0,
                0,
                0,
                Map.of(),
                Map.of(),
                false,
                0,
                1,
                List.of(),
                clique.timeLimitMillis(),
                clique.maxCliques(),
                clique.maxCliqueArrayBytes(),
                clique.inputIdentityMode(),
                clique.inputFingerprintAvailable(),
                clique.projection(),
                clique.analysisScope(),
                clique.dataFingerprint(),
                clique.inputFiles(),
                clique.inputContractVersion(),
                clique.relationProjectionVersion(),
                clique.algorithmVersion(),
                clique.algorithmParameters(),
                clique.cacheKey(),
                false));

        assertEquals(clique, restartedStore.loadClique("clique-key").orElseThrow());
    }

    @Test
    void restoresPageEvidenceInputAndCommunityPairFindingsAcrossStoreInstances() {
        SocialNetworkAnalysisResultStore store =
                new SocialNetworkAnalysisResultStore(objectMapper, tempDir);
        SocialNetworkWorkspaceService.GroupEvidenceInput input =
                new SocialNetworkWorkspaceService.GroupEvidenceInput(
                        new SocialNetworkGraphResult(
                                List.of(
                                        new NetworkNode(
                                                "a", NetworkNodeType.PROFILE, 0, 1, 0, false),
                                        new NetworkNode(
                                                "b", NetworkNodeType.PROFILE, 1, 0, 0, false)),
                                List.of(new NetworkEdge("a", "b", "following")),
                                null,
                                null,
                                List.of(),
                                CommunityDetectionResult.notRequested(),
                                null,
                                null),
                        List.of(new NormalizedUser(
                                "users.csv",
                                2,
                                "a",
                                Map.of("username", "alice"),
                                NormalizationStatus.NORMALIZED,
                                Set.of())),
                        List.of(new NormalizedRelationship(
                                "relationships.csv",
                                3,
                                0,
                                "a",
                                "b",
                                AssociationStatus.LINKED,
                                AssociationStatus.LINKED,
                                RelationStatus.VALID,
                                NormalizationStatus.NORMALIZED,
                                true)));
        List<SocialNetworkGroupInsightCalculator.CommunityPair> pairs = List.of(
                new SocialNetworkGroupInsightCalculator.CommunityPair(
                        "C001", "C002", 1, 1));

        store.saveGroupEvidenceInput(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                "fingerprint",
                input);
        store.saveCommunityPairs(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                "analysis-key",
                "fingerprint",
                pairs);

        SocialNetworkAnalysisResultStore restartedStore =
                new SocialNetworkAnalysisResultStore(objectMapper, tempDir);
        assertEquals(
                input,
                restartedStore.loadGroupEvidenceInput(
                                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                                "fingerprint")
                        .orElseThrow());
        assertEquals(
                pairs,
                restartedStore.loadCommunityPairs(
                                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                                "analysis-key",
                                "fingerprint")
                        .orElseThrow());
        assertTrue(restartedStore.loadGroupEvidenceInput(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                "changed-fingerprint").isEmpty());
        assertTrue(restartedStore.loadCommunityPairs(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                "changed-key",
                "fingerprint").isEmpty());
    }

    @Test
    void uploadSessionResultsAreNeverWrittenToPersistentStore() {
        SocialNetworkAnalysisResultStore store =
                new SocialNetworkAnalysisResultStore(objectMapper, tempDir);
        var input = new SocialNetworkWorkspaceService.GroupEvidenceInput(
                null, List.of(), List.of());
        var pairs = List.of(new SocialNetworkGroupInsightCalculator.CommunityPair(
                "C001", "C002", 1, 1));

        store.saveGroupEvidenceInput("upload-session-a", "session-a", "fingerprint", input);
        store.saveCommunityPairs(
                "upload-session-a", "session-a", "cache-key", "fingerprint", pairs);

        assertTrue(store.loadGroupEvidenceInput(
                "upload-session-a", "session-a", "fingerprint").isEmpty());
        assertTrue(store.loadCommunityPairs(
                "upload-session-a", "session-a", "cache-key", "fingerprint").isEmpty());
        assertTrue(Files.notExists(tempDir.resolve("group-evidence-v1.json")));
        assertTrue(Files.notExists(tempDir.resolve("louvain-community-pairs-v2.json")));
    }

    @Test
    void restoresBackedUpProductionResultsWhenBackupDirectoryIsProvided() {
        String backupDirectory = System.getProperty("social.network.analysis.backup");
        Assumptions.assumeTrue(
                backupDirectory != null && !backupDirectory.isBlank(),
                "Set social.network.analysis.backup to validate the production backup files");
        SocialNetworkAnalysisResultStore store =
                new SocialNetworkAnalysisResultStore(
                        objectMapper, Path.of(backupDirectory));

        CliqueEnumerationWorkflowView clique = store.loadClique(
                "11014678cbaa852b1763254c2fe58a3c045c4c1a2bb68d1089804c9ba02acd02")
                .orElseThrow();
        LouvainAnalysisWorkflowView louvain = store.loadLouvain(
                "c7156418b19134a2e43da0cd9769d71e48a483fd3cbab7f0c3f3da483ac61108")
                .orElseThrow();

        assertEquals("8e4ef44ab6416ea31fee230355b1db1407a969d823b12e5edbdc353a51858855",
                clique.dataFingerprint());
        assertEquals(50_250, clique.enumeratedCliqueCount());
        assertEquals(50_250, clique.cliques().size());
        assertTrue(clique.membershipListComplete());
        assertEquals(clique.dataFingerprint(), louvain.dataFingerprint());
        assertEquals(6, louvain.communities().size());
        assertEquals(541, louvain.communityByNodeId().size());
        assertEquals(85, louvain.unassignedNodeCount());
    }

    private static CliqueEnumerationWorkflowView cliqueView() {
        Instant now = Instant.parse("2026-11-02T12:00:00Z");
        return new CliqueEnumerationWorkflowView(
                "think-tank-social-network",
                "v1",
                "COMPLETED",
                true,
                null,
                now.minusSeconds(5),
                now,
                3,
                3,
                1,
                1,
                3,
                Map.of(3, 1),
                Map.of("a", 1, "b", 1, "c", 1),
                true,
                0,
                1,
                List.of(List.of("a", "b", "c")),
                30_000,
                100_000,
                2 * 1024 * 1024,
                "data_fingerprint",
                true,
                "undirected_simple_any_direction",
                "full_normalized_network",
                "data-fingerprint",
                List.of(new FileFingerprint("users", "users.csv", 10, "a".repeat(64))),
                "input-contract-v1",
                "relation-projection-v1",
                "clique-algorithm-v1",
                Map.of("minimumCliqueSize", 3),
                "clique-key",
                false);
    }

    private static LouvainAnalysisWorkflowView louvainView() {
        Instant now = Instant.parse("2026-11-02T12:00:00Z");
        return new LouvainAnalysisWorkflowView(
                "think-tank-social-network",
                "v1",
                "COMPLETED",
                true,
                true,
                true,
                "COMPLETED",
                null,
                now.minusSeconds(5),
                now,
                3,
                3,
                "louvain-algorithm-v1",
                Map.of("resolution", 1.0),
                "full_normalized_network",
                "data-fingerprint",
                List.of(new FileFingerprint("users", "users.csv", 10, "b".repeat(64))),
                "input-contract-v1",
                "relation-projection-v1",
                "undirected_simple",
                "quality-contract-v1",
                List.of(new NetworkCommunity("C001", 3, 3, 0, 2)),
                Map.of("a", "C001", "b", "C001", "c", "C001"),
                0,
                List.of(),
                5,
                new LouvainQualitySummary(
                        3, 3, 3, List.of(), 3, 2, 1, Map.of("C001", 2), 0.25),
                50,
                1024,
                2048,
                128,
                256,
                "louvain-key",
                false);
    }
}
