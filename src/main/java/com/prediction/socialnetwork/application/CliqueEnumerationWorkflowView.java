package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.application.SocialNetworkDataFingerprintService.FileFingerprint;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record CliqueEnumerationWorkflowView(
        String datasetId,
        String datasetVersion,
        String state,
        boolean complete,
        String terminationDetail,
        Instant requestedAt,
        Instant finishedAt,
        int inputNodeCount,
        int inputDirectedEdgeCount,
        int retainedCliqueCount,
        long enumeratedCliqueCount,
        int largestObservedCliqueSize,
        Map<Integer, Integer> cliqueSizeDistribution,
        Map<String, Integer> nodeParticipationCounts,
        boolean membershipListComplete,
        int offset,
        int pageSize,
        List<List<String>> cliques,
        long timeLimitMillis,
        int maxCliques,
        long maxCliqueArrayBytes,
        String inputIdentityMode,
        boolean inputFingerprintAvailable,
        String projection,
        String analysisScope,
        String dataFingerprint,
        List<FileFingerprint> inputFiles,
        String inputContractVersion,
        String relationProjectionVersion,
        String algorithmVersion,
        Map<String, Object> algorithmParameters,
        String cacheKey,
        boolean cacheHit) {

    public CliqueEnumerationWorkflowView {
        cliques = cliques.stream().map(List::copyOf).toList();
        inputFiles = List.copyOf(inputFiles);
        cliqueSizeDistribution = Collections.unmodifiableMap(
                new TreeMap<>(cliqueSizeDistribution));
        nodeParticipationCounts = Collections.unmodifiableMap(
                new TreeMap<>(nodeParticipationCounts));
        algorithmParameters = Collections.unmodifiableMap(
                new java.util.TreeMap<>(algorithmParameters));
    }
}
