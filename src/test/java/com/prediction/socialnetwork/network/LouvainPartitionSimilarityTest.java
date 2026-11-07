package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LouvainPartitionSimilarityTest {

    private final LouvainPartitionSimilarity similarity = new LouvainPartitionSimilarity();

    @Test
    void ignoresCommunityLabelsAndKeepsUnassignedNodesAsSingletons() {
        List<NetworkNode> nodes = nodes("A", "B", "C", "I");
        Map<String, String> baseline = Map.of("A", "C001", "B", "C001", "C", "C002");
        Map<String, String> samePartition = Map.of("A", "group-x", "B", "group-x", "C", "group-y");

        LouvainPartitionSimilarity.Similarity result =
                similarity.compare(nodes, baseline, samePartition);

        assertEquals(4, result.nodeCount());
        assertEquals(1.0, result.adjustedRandIndex(), 1e-12);
        assertEquals(1.0, result.normalizedMutualInformation(), 1e-12);
    }

    @Test
    void distinguishesOrthogonalTwoByTwoPartitions() {
        List<NetworkNode> nodes = nodes("A", "B", "C", "D");
        Map<String, String> baseline = Map.of(
                "A", "left", "B", "left", "C", "right", "D", "right");
        Map<String, String> candidate = Map.of(
                "A", "upper", "C", "upper", "B", "lower", "D", "lower");

        LouvainPartitionSimilarity.Similarity result =
                similarity.compare(nodes, baseline, candidate);

        assertEquals(-0.5, result.adjustedRandIndex(), 1e-12);
        assertEquals(0.0, result.normalizedMutualInformation(), 1e-12);
    }

    @Test
    void reportsPerfectSimilarityWhenEveryNodeIsUnassigned() {
        LouvainPartitionSimilarity.Similarity result = similarity.compare(
                nodes("A", "B", "C"), Map.of(), Map.of());

        assertEquals(1.0, result.adjustedRandIndex(), 1e-12);
        assertEquals(1.0, result.normalizedMutualInformation(), 1e-12);
    }

    private static List<NetworkNode> nodes(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> new NetworkNode(
                        id, NetworkNodeType.PROFILE, 0, 0, 0.0, false))
                .toList();
    }
}
