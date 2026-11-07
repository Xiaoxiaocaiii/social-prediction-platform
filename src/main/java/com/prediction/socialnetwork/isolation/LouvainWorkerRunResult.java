package com.prediction.socialnetwork.isolation;

import com.prediction.socialnetwork.network.CommunityDetectionResult;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
import com.prediction.socialnetwork.network.LouvainQualitySummary;

public record LouvainWorkerRunResult(
        Status status,
        String algorithmVersion,
        double resolution,
        LouvainAnalysisParameters parameters,
        CommunityDetectionResult detection,
        LouvainQualitySummary quality,
        String terminationDetail,
        long elapsedMillis,
        long peakJobMemoryBytes,
        long totalCpuTime100Nanoseconds,
        long requestBytes,
        long resultArtifactBytes) {

    public LouvainWorkerRunResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == Status.COMPLETED
                ? algorithmVersion == null
                        || !Double.isFinite(resolution)
                        || resolution <= 0
                        || parameters == null
                        || Double.compare(resolution, parameters.resolution()) != 0
                        || detection == null
                        || quality == null
                        || terminationDetail != null
                : algorithmVersion != null || resolution != 0.0
                        || parameters != null || detection != null || quality != null
                        || terminationDetail == null || terminationDetail.isBlank()) {
            throw new IllegalArgumentException(
                    "Only completed Worker runs may contain analysis results");
        }
        if (elapsedMillis < 0
                || peakJobMemoryBytes < 0
                || totalCpuTime100Nanoseconds < 0
                || requestBytes < 0
                || resultArtifactBytes < 0) {
            throw new IllegalArgumentException("Worker metrics must not be negative");
        }
    }

    public enum Status {
        COMPLETED,
        TIME_LIMIT,
        MEMORY_LIMIT,
        CANCELLED,
        BUSY,
        FAILED
    }
}
