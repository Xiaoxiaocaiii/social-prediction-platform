package com.prediction.socialnetwork.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record SocialNetworkDataset(
        String datasetId,
        String name,
        DatasetType type,
        String datasetVersion,
        DatasetStatus status,
        String description,
        String sourceDescription,
        LocalDate timeRangeFrom,
        LocalDate timeRangeTo,
        List<DatasetFileDescriptor> files,
        String sessionId,
        String normalizationVersion,
        String metricVersion,
        String textProcessingVersion,
        String explanationVersion) {

    public SocialNetworkDataset {
        datasetId = requireText(datasetId, "datasetId");
        name = requireText(name, "name");
        type = Objects.requireNonNull(type, "type");
        datasetVersion = requireText(datasetVersion, "datasetVersion");
        status = Objects.requireNonNull(status, "status");
        description = requireText(description, "description");
        sourceDescription = requireText(sourceDescription, "sourceDescription");
        files = List.copyOf(Objects.requireNonNull(files, "files"));
        normalizationVersion = requireText(normalizationVersion, "normalizationVersion");
        metricVersion = requireText(metricVersion, "metricVersion");
        textProcessingVersion = requireText(textProcessingVersion, "textProcessingVersion");
        explanationVersion = requireText(explanationVersion, "explanationVersion");

        if (timeRangeFrom != null && timeRangeTo != null && timeRangeFrom.isAfter(timeRangeTo)) {
            throw new IllegalArgumentException("timeRangeFrom must not be after timeRangeTo");
        }
        if (type == DatasetType.BUILTIN && sessionId != null) {
            throw new IllegalArgumentException("builtin dataset must not have a sessionId");
        }
        if (type == DatasetType.UPLOAD_SESSION && (sessionId == null || sessionId.isBlank())) {
            throw new IllegalArgumentException("upload dataset must have a sessionId");
        }
    }

    public boolean isUploadSession() {
        return type == DatasetType.UPLOAD_SESSION;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
