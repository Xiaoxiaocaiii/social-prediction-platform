package com.prediction.socialnetwork.export;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Traceability metadata shared by CSV, JSON, and printable exports.
 */
public record SocialNetworkExportMetadata(
        String datasetId,
        String datasetVersion,
        String normalizationVersion,
        String metricVersion,
        String textProcessingVersion,
        String explanationVersion,
        Map<String, Object> query,
        Map<String, Object> scope,
        Map<String, Object> sampleCounts,
        String sourceDescription,
        Instant exportedAt,
        List<String> boundaryNotes) {

    public SocialNetworkExportMetadata {
        datasetId = requireText(datasetId, "datasetId");
        datasetVersion = requireText(datasetVersion, "datasetVersion");
        normalizationVersion = requireText(normalizationVersion, "normalizationVersion");
        metricVersion = requireText(metricVersion, "metricVersion");
        textProcessingVersion = requireText(textProcessingVersion, "textProcessingVersion");
        explanationVersion = requireText(explanationVersion, "explanationVersion");
        sourceDescription = requireText(sourceDescription, "sourceDescription");
        exportedAt = Objects.requireNonNull(exportedAt, "exportedAt");
        query = immutableMap(query, "query");
        scope = immutableMap(scope, "scope");
        sampleCounts = immutableMap(sampleCounts, "sampleCounts");
        boundaryNotes = List.copyOf(Objects.requireNonNull(boundaryNotes, "boundaryNotes"));
    }

    private static Map<String, Object> immutableMap(
            Map<String, Object> values,
            String fieldName) {
        return Map.copyOf(Objects.requireNonNull(values, fieldName));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
