package com.prediction.socialnetwork.normalization;

import java.util.List;

public record NormalizationReport(
        int rawUserCount,
        int normalizedUserCount,
        int uniqueUserIdCount,
        int duplicateUserCount,
        int rawRelationshipRecordCount,
        int candidateRelationshipCount,
        int normalizedRelationshipCount,
        int duplicateRelationshipCount,
        int selfLoopCount,
        int externalNodeCount,
        int rawTweetCount,
        int uniqueTweetIdCount,
        int duplicateTweetCount,
        int conflictDuplicateCount,
        int linkedTweetCount,
        int unlinkedAuthorCount,
        int unassignedSourceCount,
        List<NormalizationIssue> issues) {

    public NormalizationReport {
        issues = List.copyOf(issues);
    }
}
