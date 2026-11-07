package com.prediction.socialnetwork.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record CliqueEnumerationResult(
        List<List<String>> cliques,
        CliqueEnumerationStatus status,
        CliqueEnumerationLimits limits,
        long encodedCliqueArrayBytes,
        String terminationDetail,
        long enumeratedCliqueCount,
        Map<Integer, Integer> cliqueSizeDistribution,
        Map<String, Integer> nodeParticipationCounts,
        boolean membershipListComplete) {

    public CliqueEnumerationResult {
        cliques = cliques.stream().map(List::copyOf).toList();
        cliqueSizeDistribution = Collections.unmodifiableMap(
                new TreeMap<>(cliqueSizeDistribution));
        nodeParticipationCounts = Collections.unmodifiableMap(
                new TreeMap<>(nodeParticipationCounts));
        if (status == null || limits == null) {
            throw new IllegalArgumentException("status and limits must not be null");
        }
        if (encodedCliqueArrayBytes < 2
                || encodedCliqueArrayBytes > limits.maxCliqueArrayBytes()) {
            throw new IllegalArgumentException("encodedCliqueArrayBytes is outside the limit");
        }
        if (enumeratedCliqueCount < cliques.size()
                || enumeratedCliqueCount > limits.maxCliques()
                || (membershipListComplete && enumeratedCliqueCount != cliques.size())) {
            throw new IllegalArgumentException("Clique membership totals are inconsistent");
        }
        long distributionTotal = 0;
        for (Map.Entry<Integer, Integer> entry : cliqueSizeDistribution.entrySet()) {
            if (entry.getKey() < MaximalCliqueEnumerator.MINIMUM_CLIQUE_SIZE
                    || entry.getValue() < 1) {
                throw new IllegalArgumentException("Clique size distribution is invalid");
            }
            distributionTotal += entry.getValue();
        }
        if (distributionTotal != enumeratedCliqueCount) {
            throw new IllegalArgumentException("Clique size distribution total is invalid");
        }
        Map<Integer, Integer> remainingSizes = new TreeMap<>(cliqueSizeDistribution);
        Map<String, Integer> remainingParticipation = new TreeMap<>(nodeParticipationCounts);
        for (List<String> clique : cliques) {
            if (clique.size() < MaximalCliqueEnumerator.MINIMUM_CLIQUE_SIZE
                    || remainingSizes.getOrDefault(clique.size(), 0) < 1
                    || !decrement(remainingSizes, clique.size())) {
                throw new IllegalArgumentException("Retained clique list exceeds its summary");
            }
            for (String node : clique) {
                if (node == null || node.isBlank() || !decrement(remainingParticipation, node)) {
                    throw new IllegalArgumentException(
                            "Retained clique list exceeds node participation summary");
                }
            }
        }
        long participationTotal = 0;
        for (Map.Entry<String, Integer> entry : nodeParticipationCounts.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() < 0) {
                throw new IllegalArgumentException("Node participation counts are invalid");
            }
            participationTotal += entry.getValue();
        }
        long expectedParticipationTotal = cliqueSizeDistribution.entrySet().stream()
                .mapToLong(entry -> (long) entry.getKey() * entry.getValue())
                .sum();
        if (participationTotal != expectedParticipationTotal) {
            throw new IllegalArgumentException("Node participation total is invalid");
        }
        if (status == CliqueEnumerationStatus.FAILED
                && (terminationDetail == null || terminationDetail.isBlank())) {
            throw new IllegalArgumentException("FAILED results require a termination detail");
        }
    }

    public CliqueEnumerationResult(
            List<List<String>> cliques,
            CliqueEnumerationStatus status,
            CliqueEnumerationLimits limits,
            long encodedCliqueArrayBytes,
            String terminationDetail) {
        this(
                cliques,
                status,
                limits,
                encodedCliqueArrayBytes,
                terminationDetail,
                cliques.size(),
                sizeDistribution(cliques),
                participationCounts(cliques),
                true);
    }

    public static CliqueEnumerationResult externalTermination(
            CliqueEnumerationStatus status,
            CliqueEnumerationLimits limits,
            String detail) {
        if (status != CliqueEnumerationStatus.TIME_LIMIT
                && status != CliqueEnumerationStatus.MEMORY_LIMIT
                && status != CliqueEnumerationStatus.CANCELLED
                && status != CliqueEnumerationStatus.BUSY
                && status != CliqueEnumerationStatus.RESULT_SIZE_LIMIT
                && status != CliqueEnumerationStatus.FAILED) {
            throw new IllegalArgumentException("Status is not an external termination");
        }
        return new CliqueEnumerationResult(
                List.of(), status, limits, 2, detail, 0, Map.of(), Map.of(), false);
    }

    public boolean isComplete() {
        return status == CliqueEnumerationStatus.COMPLETED;
    }

    public int retainedCliqueCount() {
        return cliques.size();
    }

    public int largestObservedCliqueSize() {
        return cliqueSizeDistribution.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    public Map<String, Integer> observedParticipationCounts() {
        return nodeParticipationCounts;
    }

    private static Map<Integer, Integer> sizeDistribution(List<List<String>> cliques) {
        Map<Integer, Integer> counts = new TreeMap<>();
        cliques.forEach(clique -> counts.merge(clique.size(), 1, Integer::sum));
        return counts;
    }

    private static <K> boolean decrement(Map<K, Integer> counts, K key) {
        Integer count = counts.get(key);
        if (count == null || count < 1) {
            return false;
        }
        if (count == 1) {
            counts.remove(key);
        } else {
            counts.put(key, count - 1);
        }
        return true;
    }

    private static Map<String, Integer> participationCounts(List<List<String>> cliques) {
        Map<String, Integer> counts = new TreeMap<>();
        cliques.forEach(clique -> clique.forEach(node ->
                counts.merge(node, 1, Integer::sum)));
        return counts;
    }

    public List<List<String>> page(int offset, int pageSize) {
        if (offset < 0 || pageSize < 1) {
            throw new IllegalArgumentException("offset must be non-negative and pageSize positive");
        }
        if (offset >= cliques.size()) {
            return List.of();
        }
        int end = (int) Math.min(cliques.size(), (long) offset + pageSize);
        return List.copyOf(new ArrayList<>(cliques.subList(offset, end)));
    }
}
