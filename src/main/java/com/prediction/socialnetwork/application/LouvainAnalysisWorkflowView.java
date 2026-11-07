package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.application.SocialNetworkDataFingerprintService.FileFingerprint;
import com.prediction.socialnetwork.network.LouvainQualitySummary;
import com.prediction.socialnetwork.network.NetworkCommunity;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record LouvainAnalysisWorkflowView(
        String datasetId,
        String datasetVersion,
        String state,
        boolean complete,
        boolean workerCompleted,
        boolean converged,
        String workerStatus,
        String terminationDetail,
        Instant requestedAt,
        Instant finishedAt,
        int inputNodeCount,
        int inputDirectedRelationshipCount,
        String algorithmVersion,
        Map<String, Object> algorithmParameters,
        String analysisScope,
        String dataFingerprint,
        List<FileFingerprint> inputFiles,
        String inputContractVersion,
        String relationProjectionVersion,
        String projection,
        String qualityContractVersion,
        List<NetworkCommunity> communities,
        Map<String, String> communityByNodeId,
        int unassignedNodeCount,
        List<String> unassignedNodeIds,
        int iterations,
        LouvainQualitySummary quality,
        long elapsedMillis,
        long peakJobMemoryBytes,
        long totalCpuTime100Nanoseconds,
        long requestBytes,
        long resultArtifactBytes,
        String cacheKey,
        boolean cacheHit) {

    public LouvainAnalysisWorkflowView {
        algorithmParameters = Collections.unmodifiableMap(new TreeMap<>(algorithmParameters));
        inputFiles = List.copyOf(inputFiles);
        communities = List.copyOf(communities);
        communityByNodeId = Collections.unmodifiableMap(new TreeMap<>(communityByNodeId));
        unassignedNodeIds = List.copyOf(unassignedNodeIds);
    }
}
