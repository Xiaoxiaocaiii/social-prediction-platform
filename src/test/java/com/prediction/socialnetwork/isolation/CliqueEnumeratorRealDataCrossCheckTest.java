package com.prediction.socialnetwork.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.application.CliqueEvidenceAggregator;
import com.prediction.socialnetwork.application.SocialNetworkDatasetRegistry;
import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.network.SocialNetworkCalculator;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.normalization.SocialNetworkNormalizer;
import com.prediction.socialnetwork.reader.FileReadResult;
import com.prediction.socialnetwork.reader.SocialNetworkFileReader;
import com.prediction.socialnetwork.network.CliqueEnumerationIndependentVerifier;
import com.prediction.socialnetwork.network.CliqueEnumerationResult;
import com.prediction.socialnetwork.network.CliqueEnumerationWorkerMain;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.RelationStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class CliqueEnumeratorRealDataCrossCheckTest {

    private static final Path JAVA_EXECUTABLE =
            Path.of(System.getProperty("java.home"), "bin", "javaw.exe");
    private static final String WORKER_CLASS_PATH = System.getProperty(
            "surefire.test.class.path",
            System.getProperty("java.class.path", ""));

    @Test
    @EnabledIfSystemProperty(named = "clique.crosscheck.real", matches = "true")
    void bitSetWorkerMatchesTheProductionWorkerOnTheBuiltInDataset()
            throws NoSuchAlgorithmException {
        var registry = new SocialNetworkDatasetRegistry();
        SocialNetworkDataset dataset =
                registry.findDataset(SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
        assertEquals(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                dataset.datasetId());
        assertEquals(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                dataset.datasetVersion());
        DatasetFileDescriptor usersDescriptor = descriptor(dataset, DatasetFileRole.USERS);
        DatasetFileDescriptor relationshipsDescriptor =
                descriptor(dataset, DatasetFileRole.RELATIONSHIPS);
        SocialNetworkFileReader reader = new SocialNetworkFileReader();
        FileReadResult usersFile = reader.read(
                Path.of(usersDescriptor.logicalFileName()), usersDescriptor, true);
        FileReadResult relationshipsFile = reader.read(
                Path.of(relationshipsDescriptor.logicalFileName()),
                relationshipsDescriptor,
                true);
        assertTrue(usersFile.report().isUsable());
        assertTrue(relationshipsFile.report().isUsable());
        assertEquals(0, usersFile.report().readErrorCount());
        assertEquals(0, relationshipsFile.report().readErrorCount());
        var normalized = new SocialNetworkNormalizer().normalize(
                usersFile.records(), relationshipsFile.records(), List.of());
        SocialNetworkGraphResult network = new SocialNetworkCalculator()
                .calculateWithoutCommunities(normalized.users(), normalized.relationships());

        WindowsCliqueEnumerationRunner productionRunner = new WindowsCliqueEnumerationRunner(
                JAVA_EXECUTABLE, WORKER_CLASS_PATH, WindowsJobLimits.DEFAULT,
                CliqueEnumerationWorkerMain.class.getName());
        long productionStartedAt = System.nanoTime();
        CliqueEnumerationResult production = productionRunner.run(
                network.nodes(), network.edges(), () -> false);
        long productionMillis = elapsedMillis(productionStartedAt);
        assertTrue(production.isComplete(), "production worker must complete");
        assertTrue(production.membershipListComplete(), "production memberships must be complete");
        CrossCheckSummary productionSummary = summarize(production, network);
        long evidenceStartedAt = System.nanoTime();
        EvidenceSummary evidenceSummary = verifyCompleteEvidence(
                production,
                network,
                normalized.users(),
                normalized.relationships());
        long evidenceMillis = elapsedMillis(evidenceStartedAt);
        production = null;

        System.gc();
        WindowsCliqueEnumerationRunner bitSetRunner = new WindowsCliqueEnumerationRunner(
                JAVA_EXECUTABLE, WORKER_CLASS_PATH, WindowsJobLimits.DEFAULT,
                BitSetCliqueCrossCheckWorkerMain.class.getName());
        long bitSetStartedAt = System.nanoTime();
        CliqueEnumerationResult bitSet = bitSetRunner.run(
                network.nodes(), network.edges(), () -> false);
        long bitSetMillis = elapsedMillis(bitSetStartedAt);
        assertTrue(bitSet.isComplete(), "independent BitSet worker must complete");
        CrossCheckSummary bitSetSummary = summarize(bitSet, network);
        assertEquals(productionSummary, bitSetSummary);
        System.out.printf(
                "CL08_CROSSCHECK nodes=%d directedEdges=%d cliques=%d "
                        + "memberships=%d incidenceCount=%d mainMs=%d bitSetMs=%d%n",
                bitSetSummary.verification().inputNodeCount(),
                network.edges().size(),
                bitSetSummary.verification().verifiedCliqueCount(),
                bitSetSummary.retainedCliqueListCount(),
                bitSetSummary.verification().verifiedMemberIncidences(),
                productionMillis,
                bitSetMillis);
        System.out.printf(
                "CL08_EVIDENCE cliques=%d members=%d ties=%d supportingDirections=%d "
                        + "duplicateRecords=%d elapsedMs=%d%n",
                evidenceSummary.cliqueCount(),
                evidenceSummary.memberCount(),
                evidenceSummary.tieCount(),
                evidenceSummary.supportingDirectionCount(),
                evidenceSummary.duplicateRecordCount(),
                evidenceMillis);
    }

    private static EvidenceSummary verifyCompleteEvidence(
            CliqueEnumerationResult result,
            SocialNetworkGraphResult network,
            List<NormalizedUser> users,
            List<NormalizedRelationship> relationships) {
        CliqueEvidenceAggregator aggregator = new CliqueEvidenceAggregator(
                network.nodes(), users, relationships);
        Map<String, List<CliqueEvidenceAggregator.ProfileSourceReference>> profileSources =
                new HashMap<>();
        for (NormalizedUser user : users) {
            profileSources.computeIfAbsent(user.userId(), ignored -> new ArrayList<>())
                    .add(new CliqueEvidenceAggregator.ProfileSourceReference(
                            user.sourceFileName(),
                            user.sourceRowNumber(),
                            user.normalizationStatus()));
        }
        Comparator<CliqueEvidenceAggregator.ProfileSourceReference> profileOrder =
                Comparator.comparing(
                                CliqueEvidenceAggregator.ProfileSourceReference::sourceFileName)
                        .thenComparingInt(
                                CliqueEvidenceAggregator.ProfileSourceReference::sourceRowNumber);
        profileSources.values().forEach(sources -> sources.sort(profileOrder));

        Map<EvidenceTie, Set<CliqueEvidenceAggregator.DirectedRelationshipSourceReference>>
                supportingByTie = new HashMap<>();
        Map<EvidenceTie, Set<CliqueEvidenceAggregator.DirectedRelationshipSourceReference>>
                duplicatesByTie = new HashMap<>();
        for (NormalizedRelationship relationship : relationships) {
            if (!relationship.analysisIncluded()
                    && relationship.relationStatus() != RelationStatus.DUPLICATE) {
                continue;
            }
            EvidenceTie tie = EvidenceTie.of(
                    relationship.sourceUserId(), relationship.targetUserId());
            CliqueEvidenceAggregator.DirectedRelationshipSourceReference reference =
                    new CliqueEvidenceAggregator.DirectedRelationshipSourceReference(
                            relationship.sourceFileName(),
                            relationship.sourceRowNumber(),
                            relationship.sourceArrayIndex(),
                            relationship.sourceUserId(),
                            relationship.targetUserId());
            Map<EvidenceTie,
                    Set<CliqueEvidenceAggregator.DirectedRelationshipSourceReference>> target =
                            relationship.analysisIncluded()
                                    ? supportingByTie
                                    : duplicatesByTie;
            target.computeIfAbsent(tie, ignored -> new HashSet<>()).add(reference);
        }

        long[] counts = new long[5];
        aggregator.forEachCompleteResult(result, clique -> {
            counts[0]++;
            counts[1] += clique.members().size();
            for (CliqueEvidenceAggregator.MemberEvidence member : clique.members()) {
                assertEquals(
                        profileSources.getOrDefault(member.nodeId(), List.of()),
                        member.profileSources(),
                        "profile sources must match normalized CSV rows for " + member.nodeId());
            }
            counts[2] += clique.ties().size();
            for (CliqueEvidenceAggregator.TieEvidence tie : clique.ties()) {
                EvidenceTie key = EvidenceTie.of(tie.leftNodeId(), tie.rightNodeId());
                assertEquals(1, tie.weight());
                assertEquals(
                        supportingByTie.getOrDefault(key, Set.of()),
                        Set.copyOf(tie.supportingRelationships()),
                        "all valid source directions must be retained for " + key);
                assertEquals(
                        duplicatesByTie.getOrDefault(key, Set.of()),
                        Set.copyOf(tie.duplicateRelationshipRecords()),
                        "duplicate source rows must remain auditable for " + key);
                counts[3] += tie.supportingRelationships().size();
                counts[4] += tie.duplicateRelationshipRecords().size();
            }
        });
        assertEquals(result.enumeratedCliqueCount(), counts[0]);
        return new EvidenceSummary(counts[0], counts[1], counts[2], counts[3], counts[4]);
    }

    private static CrossCheckSummary summarize(
            CliqueEnumerationResult result,
            SocialNetworkGraphResult network)
            throws NoSuchAlgorithmException {
        var verification = new CliqueEnumerationIndependentVerifier().verify(
                network.nodes(), network.edges(), result);
        List<List<String>> orderedCliques = new ArrayList<>(result.cliques());
        orderedCliques.sort(CliqueEnumeratorRealDataCrossCheckTest::compareCliques);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (List<String> clique : orderedCliques) {
            for (String member : clique) {
                byte[] bytes = member.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (bytes.length >>> 24));
                digest.update((byte) (bytes.length >>> 16));
                digest.update((byte) (bytes.length >>> 8));
                digest.update((byte) bytes.length);
                digest.update(bytes);
            }
            digest.update((byte) 0xff);
        }
        return new CrossCheckSummary(
                result.enumeratedCliqueCount(),
                result.retainedCliqueCount(),
                result.cliqueSizeDistribution(),
                result.nodeParticipationCounts(),
                result.encodedCliqueArrayBytes(),
                HexFormat.of().formatHex(digest.digest()),
                verification);
    }

    private static DatasetFileDescriptor descriptor(
            SocialNetworkDataset dataset, DatasetFileRole role) {
        return dataset.files().stream()
                .filter(file -> file.role() == role)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Built-in dataset is missing file role " + role));
    }

    private static int compareCliques(List<String> left, List<String> right) {
        int sharedLength = Math.min(left.size(), right.size());
        for (int index = 0; index < sharedLength; index++) {
            int comparison = left.get(index).compareTo(right.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private record CrossCheckSummary(
            long cliqueCount,
            int retainedCliqueListCount,
            Map<Integer, Integer> sizeDistribution,
            Map<String, Integer> nodeParticipation,
            long encodedMembershipBytes,
            String membershipSha256,
            CliqueEnumerationIndependentVerifier.VerificationReport verification) {
    }

    private record EvidenceTie(String leftId, String rightId) {

        private static EvidenceTie of(String leftId, String rightId) {
            return leftId.compareTo(rightId) < 0
                    ? new EvidenceTie(leftId, rightId)
                    : new EvidenceTie(rightId, leftId);
        }
    }

    private record EvidenceSummary(
            long cliqueCount,
            long memberCount,
            long tieCount,
            long supportingDirectionCount,
            long duplicateRecordCount) {
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
