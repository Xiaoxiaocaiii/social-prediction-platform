package com.prediction.socialnetwork.quality;

import java.util.List;

public record QualityIssue(
        String code,
        QualitySeverity severity,
        String dimension,
        String message,
        String sourceFileName,
        List<Integer> sourceRowNumbers) {

    public QualityIssue {
        sourceRowNumbers = List.copyOf(sourceRowNumbers);
    }
}
