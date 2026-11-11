package com.prediction.socialnetwork.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.prediction.socialnetwork.application.SocialNetworkDatasetRegistry;
import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
import com.prediction.socialnetwork.network.LouvainPartitionSimilarity;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.SocialNetworkCalculator;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.normalization.SocialNetworkNormalizer;
import com.prediction.socialnetwork.reader.FileReadResult;
import com.prediction.socialnetwork.reader.SocialNetworkFileReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class LouvainRealDataSensitivityTest {

    @Test
    @EnabledIfSystemProperty(named = "louvain.real.sensitivity", matches = "true")
    void comparesBoundedRealDataVariantsAgainstThePrimaryPartition() {
        Assumptions.assumeTrue(
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows"),
                "Real-data Louvain sensitivity requires Windows Job Object support");

        SocialNetworkDatasetRegistry registry = new SocialNetworkDatasetRegistry();
        SocialNetworkDataset dataset = registry.findDataset(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID);
        DatasetFileDescriptor usersDescriptor = descriptor(dataset, DatasetFileRole.USERS);
        DatasetFileDescriptor relationshipsDescriptor =
                descriptor(dataset, DatasetFileRole.RELATIONSHIPS);
        SocialNetworkFileReader reader = new SocialNetworkFileReader();
        FileReadResult usersFile =
                reader.read(Path.of(usersDescriptor.logicalFileName()), usersDescriptor, true);
        FileReadResult relationshipsFile = reader.read(
                Path.of(relationshipsDescriptor.logicalFileName()),
                relationshipsDescriptor,
                true);
        if (!usersFile.report().isUsable()
                || !relationshipsFile.report().isUsable()
                || usersFile.report().readErrorCount() != 0
                || relationshipsFile.report().readErrorCount() != 0) {
            throw new IllegalStateException("Built-in network files did not load cleanly");
        }
        var normalized = new SocialNetworkNormalizer().normalize(
                usersFile.records(), relationshipsFile.records(), List.of());
        SocialNetworkGraphResult network = new SocialNetworkCalculator()
                .calculateWithoutCommunities(normalized.users(), normalized.relationships());
        assertEquals(626, network.nodes().size());
        assertEquals(12_963, network.edges().size());

        List<LouvainAnalysisParameters> scenarios = List.of(
                LouvainAnalysisParameters.DEFAULT,
                new LouvainAnalysisParameters(
                        0.5,
                        LouvainAnalysisParameters.NodeOrder.DEGREE_DESCENDING_THEN_ID_ASCENDING,
                        LouvainAnalysisParameters.RelationProjection.ANY_DIRECTION),
                new LouvainAnalysisParameters(
                        2.0,
                        LouvainAnalysisParameters.NodeOrder.DEGREE_DESCENDING_THEN_ID_ASCENDING,
                        LouvainAnalysisParameters.RelationProjection.ANY_DIRECTION),
                new LouvainAnalysisParameters(
                        1.0,
                        LouvainAnalysisParameters.NodeOrder.DEGREE_ASCENDING_THEN_ID_ASCENDING,
                        LouvainAnalysisParameters.RelationProjection.ANY_DIRECTION),
                new LouvainAnalysisParameters(
                        1.0,
                        LouvainAnalysisParameters.NodeOrder.DEGREE_DESCENDING_THEN_ID_ASCENDING,
                        LouvainAnalysisParameters.RelationProjection.MUTUAL_ONLY));

        WindowsLouvainAnalysisRunner runner = new WindowsLouvainAnalysisRunner();
        LouvainWorkerRunResult baseline = null;
        LouvainPartitionSimilarity similarity = new LouvainPartitionSimilarity();
        for (int index = 0; index < scenarios.size(); index++) {
            LouvainAnalysisParameters parameters = scenarios.get(index);
            LouvainWorkerRunResult result =
                    runner.run(network.nodes(), network.edges(), parameters, () -> false);
            printVariant(
                    index == 0 ? "baseline" : "variant-" + index,
                    parameters,
                    result,
                    baseline,
                    network.nodes(),
                    similarity);
            assertEquals(LouvainWorkerRunResult.Status.COMPLETED, result.status());
            if (index == 0) {
                baseline = result;
            }
        }
    }

    private static void printVariant(
            String name,
            LouvainAnalysisParameters parameters,
            LouvainWorkerRunResult result,
            LouvainWorkerRunResult baseline,
            List<NetworkNode> inputNodes,
            LouvainPartitionSimilarity similarity) {
        LouvainPartitionSimilarity.Similarity comparison =
                baseline == null && result.status() == LouvainWorkerRunResult.Status.COMPLETED
                        ? new LouvainPartitionSimilarity.Similarity(
                                inputNodes.size(), 1.0, 1.0)
                        : baseline == null
                                || result.status() != LouvainWorkerRunResult.Status.COMPLETED
                                        ? null
                                        : similarity.compare(
                        inputNodes,
                        baseline.detection().communityByNodeId(),
                        result.detection().communityByNodeId());
        double ari = comparison == null ? -1.0 : comparison.adjustedRandIndex();
        double nmi = comparison == null ? -1.0 : comparison.normalizedMutualInformation();
        System.out.printf(
                Locale.ROOT,
                "CL12_VARIANT name=%s status=%s resolution=%.2f nodeOrder=%s projection=%s "
                        + "nodes=%d directedRelationships=%d assigned=%d unassigned=%d "
                        + "ties=%d internal=%d cross=%d "
                        + "communities=%s modularityAtGamma1=%s iterations=%d converged=%s "
                        + "ariVsBaseline=%.12f nmiVsBaseline=%.12f elapsedMs=%d "
                        + "peakJobMemoryBytes=%d cpuTime100ns=%d requestBytes=%d resultBytes=%d "
                        + "detail=%s%n",
                name,
                result.status(),
                parameters.resolution(),
                parameters.nodeOrder(),
                parameters.relationProjection(),
                inputNodes.size(),
                result.quality() == null
                        ? -1
                        : result.quality().inputDirectedRelationshipCount(),
                result.quality() == null ? -1 : result.quality().assignedNodeCount(),
                result.quality() == null ? -1 : result.quality().unassignedNodeCount(),
                result.quality() == null ? -1 : result.quality().projectedUndirectedTieCount(),
                result.quality() == null ? -1 : result.quality().internalTieCount(),
                result.quality() == null ? -1 : result.quality().crossCommunityTieCount(),
                result.detection() == null
                        ? List.of()
                        : result.detection().communities().stream()
                                .map(community -> community.nodeCount())
                                .toList(),
                result.quality() == null ? null : result.quality().modularity(),
                result.detection() == null ? -1 : result.detection().iterations(),
                result.detection() != null && result.detection().converged(),
                ari,
                nmi,
                result.elapsedMillis(),
                result.peakJobMemoryBytes(),
                result.totalCpuTime100Nanoseconds(),
                result.requestBytes(),
                result.resultArtifactBytes(),
                result.terminationDetail());
    }

    private static DatasetFileDescriptor descriptor(
            SocialNetworkDataset dataset, DatasetFileRole role) {
        return dataset.files().stream()
                .filter(candidate -> candidate.role() == role)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing built-in " + role + " file"));
    }
}
