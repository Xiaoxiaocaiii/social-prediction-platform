package com.prediction.socialnetwork.normalization;

public record NormalizationIssue(
        String sourceFileName,
        int sourceRowNumber,
        String code,
        String message) {
}
