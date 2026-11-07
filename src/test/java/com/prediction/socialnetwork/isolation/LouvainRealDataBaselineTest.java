package com.prediction.socialnetwork.isolation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prediction.socialnetwork.application.SocialNetworkDatasetRegistry;
import com.prediction.socialnetwork.domain.DatasetFileDescriptor;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.network.LouvainCommunityDetector;
import com.prediction.socialnetwork.network.NetworkCommunity;
import com.prediction.socialnetwork.network.SocialNetworkCalculator;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.normalization.SocialNetworkNormalizer;
import com.prediction.socialnetwork.reader.FileReadResult;
import com.prediction.socialnetwork.reader.SocialNetworkFileReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class LouvainRealDataBaselineTest {

    private static final long MINIMUM_PARENT_HEAP_BYTES =
            768L * 1024L * 1024L;
    private static final long MINIMUM_AVAILABLE_MEMORY_BYTES =
            2L * WindowsJobLimits.GIBIBYTE;

    @Test
    @EnabledIfSystemProperty(named = "louvain.real", matches = "true")
    void calculatesTheCompleteBuiltInDatasetInsideTheBoundedWorker() {
        Assumptions.assumeTrue(
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows"),
                "Louvain real-data baseline requires Windows Job Object support");
        Assumptions.assumeTrue(
                Runtime.getRuntime().maxMemory() >= MINIMUM_PARENT_HEAP_BYTES,
                "Run with a parent JVM heap of at least 768 MiB");
        requireMemory("before snapshot construction");

        long inputStartedAt = System.nanoTime();
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
        assertTrue(usersFile.report().isUsable());
        assertTrue(relationshipsFile.report().isUsable());
        assertEquals(0, usersFile.report().readErrorCount());
        assertEquals(0, relationshipsFile.report().readErrorCount());
        var normalized = new SocialNetworkNormalizer().normalize(
                usersFile.records(), relationshipsFile.records(), List.of());
        SocialNetworkGraphResult network = new SocialNetworkCalculator()
                .calculateWithoutCommunities(normalized.users(), normalized.relationships());
        long inputBuildMillis = elapsedMillis(inputStartedAt);
        assertEquals(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                dataset.datasetId());
        assertEquals(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                dataset.datasetVersion());

        long availableBeforeWorker =
                WindowsResourcePreflight.currentAvailablePhysicalMemoryBytes();
        System.out.printf(
                Locale.ROOT,
                "CL11_NETWORK_INPUT dataset=%s version=%s nodes=%d directedRelationships=%d "
                        + "elapsedMs=%d availableBytesBeforeWorker=%d%n",
                dataset.datasetId(),
                dataset.datasetVersion(),
                network.nodes().size(),
                network.edges().size(),
                inputBuildMillis,
                availableBeforeWorker);
        Assumptions.assumeTrue(
                WindowsResourcePreflight.hasRequiredMemory(availableBeforeWorker),
                "Skipped before worker because snapshot construction used the 2 GiB reserve");

        WindowsLouvainAnalysisRunner runner = new WindowsLouvainAnalysisRunner();
        LouvainWorkerRunResult result = runner.run(
                network.nodes(), network.edges(), () -> false);
        printResult(dataset.datasetId(), dataset.datasetVersion(), network, result);
        Assumptions.assumeTrue(
                result.status() != LouvainWorkerRunResult.Status.MEMORY_LIMIT,
                "Production Runner refused to start below its 2 GiB preflight");
        assertEquals(LouvainWorkerRunResult.Status.COMPLETED, result.status());

        assertEquals(
                network.nodes().size(),
                result.quality().inputNodeCount());
        assertEquals(
                network.edges().size(),
                result.quality().inputDirectedRelationshipCount());
        assertEquals(
                network.nodes().size(),
                result.quality().assignedNodeCount()
                        + result.quality().unassignedNodeCount());
        assertEquals(
                result.quality().projectedUndirectedTieCount(),
                result.quality().internalTieCount()
                        + result.quality().crossCommunityTieCount());
        assertEquals(
                LouvainCommunityDetector.ALGORITHM_VERSION,
                result.algorithmVersion());
        assertEquals(LouvainCommunityDetector.RESOLUTION, result.resolution());
        assertTrue(result.resultArtifactBytes() <= 2L * 1024L * 1024L);
    }

    private static void printResult(
            String datasetId,
            String datasetVersion,
            SocialNetworkGraphResult network,
            LouvainWorkerRunResult result) {
        System.out.printf(
                Locale.ROOT,
                "CL11_RESULT status=%s detail=%s elapsedMs=%d peakJobMemoryBytes=%d "
                        + "cpuTime100ns=%d requestBytes=%d resultArtifactBytes=%d%n",
                result.status(),
                result.terminationDetail(),
                result.elapsedMillis(),
                result.peakJobMemoryBytes(),
                result.totalCpuTime100Nanoseconds(),
                result.requestBytes(),
                result.resultArtifactBytes());
        if (result.detection() == null || result.quality() == null) {
            return;
        }
        System.out.printf(
                Locale.ROOT,
                "CL11_QUALITY dataset=%s version=%s inputNodes=%d "
                        + "inputDirectedRelationships=%d assignedNodes=%d "
                        + "unassignedNodes=%d projectedTies=%d internalTies=%d "
                        + "crossCommunityTies=%d modularity=%s iterations=%d converged=%s "
                        + "algorithmVersion=%s resolution=%s projection=%s%n",
                datasetId,
                datasetVersion,
                result.quality().inputNodeCount(),
                result.quality().inputDirectedRelationshipCount(),
                result.quality().assignedNodeCount(),
                result.quality().unassignedNodeCount(),
                result.quality().projectedUndirectedTieCount(),
                result.quality().internalTieCount(),
                result.quality().crossCommunityTieCount(),
                result.quality().modularity(),
                result.detection().iterations(),
                result.detection().converged(),
                result.algorithmVersion(),
                result.resolution(),
                result.detection().relationProjection());
        Map<String, List<String>> membersByCommunity =
                result.detection().communityByNodeId().entrySet().stream()
                        .collect(Collectors.groupingBy(
                                Map.Entry::getValue,
                                TreeMap::new,
                                Collectors.mapping(
                                        Map.Entry::getKey,
                                        Collectors.collectingAndThen(
                                                Collectors.toList(),
                                                values -> values.stream().sorted().toList()))));
        Map<String, NetworkCommunity> summaries = result.detection().communities().stream()
                .collect(Collectors.toMap(
                        NetworkCommunity::id, community -> community));
        membersByCommunity.forEach((communityId, members) -> {
            NetworkCommunity summary = summaries.get(communityId);
            System.out.printf(
                    Locale.ROOT,
                    "CL11_COMMUNITY id=%s size=%d profileNodes=%d externalNodes=%d "
                            + "internalTies=%d members=%s%n",
                    communityId,
                    summary.nodeCount(),
                    summary.profileNodeCount(),
                    summary.externalNodeCount(),
                    summary.internalTieCount(),
                    members);
        });
        System.out.printf(
                Locale.ROOT,
                "CL11_UNASSIGNED nodeIds=%s%n",
                result.quality().unassignedNodeIds());
        System.out.printf(
                Locale.ROOT,
                "CL11_DATASET nodeCountMatches=%s edgeCountMatches=%s%n",
                network.nodes().size() == result.quality().inputNodeCount(),
                network.edges().size()
                        == result.quality().inputDirectedRelationshipCount());
    }

    private static DatasetFileDescriptor descriptor(
            SocialNetworkDataset dataset, DatasetFileRole role) {
        return dataset.files().stream()
                .filter(candidate -> candidate.role() == role)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing built-in " + role + " file"));
    }

    private static void requireMemory(String phase) {
        Assumptions.assumeTrue(
                WindowsResourcePreflight.currentAvailablePhysicalMemoryBytes()
                        >= MINIMUM_AVAILABLE_MEMORY_BYTES,
                "Skipped " + phase + " below the 2 GiB physical-memory preflight");
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
