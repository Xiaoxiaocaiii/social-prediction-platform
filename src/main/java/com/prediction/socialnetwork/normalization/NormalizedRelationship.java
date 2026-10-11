package com.prediction.socialnetwork.normalization;

public record NormalizedRelationship(
        String sourceFileName,
        int sourceRowNumber,
        int sourceArrayIndex,
        String sourceUserId,
        String targetUserId,
        AssociationStatus sourceNodeStatus,
        AssociationStatus targetNodeStatus,
        RelationStatus relationStatus,
        NormalizationStatus normalizationStatus,
        boolean analysisIncluded) {
}
