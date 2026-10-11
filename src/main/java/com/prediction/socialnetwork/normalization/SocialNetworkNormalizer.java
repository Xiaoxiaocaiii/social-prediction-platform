package com.prediction.socialnetwork.normalization;

import com.prediction.socialnetwork.reader.RawRecord;
import com.prediction.socialnetwork.reader.SocialNetworkFileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SocialNetworkNormalizer {

    public SocialNetworkNormalizationResult normalize(
            List<RawRecord> userRecords,
            List<RawRecord> relationshipRecords,
            List<RawRecord> tweetRecords) {
        UserNormalization users = normalizeUsers(userRecords);
        RelationshipNormalization relationships =
                normalizeRelationships(relationshipRecords, users.userIds());
        TweetNormalization tweets = normalizeTweets(tweetRecords, users.userIds());

        List<NormalizationIssue> issues = new ArrayList<>();
        issues.addAll(users.issues());
        issues.addAll(relationships.issues());
        issues.addAll(tweets.issues());
        int externalNodeCount = relationships.externalNodes().size();
        NormalizationReport report = new NormalizationReport(
                userRecords.size(),
                users.records().size(),
                users.userIds().size(),
                users.duplicateCount(),
                relationshipRecords.size(),
                relationships.candidateCount(),
                relationships.records().size(),
                relationships.duplicateCount(),
                relationships.selfLoopCount(),
                externalNodeCount,
                tweetRecords.size(),
                tweets.uniqueTweetIdCount(),
                tweets.duplicateCount(),
                tweets.conflictDuplicateCount(),
                tweets.linkedCount(),
                tweets.unlinkedCount(),
                tweets.unassignedCount(),
                issues);
        return new SocialNetworkNormalizationResult(
                users.records(), relationships.records(), tweets.records(), report);
    }

    private UserNormalization normalizeUsers(List<RawRecord> records) {
        Map<String, List<RawRecord>> grouped = new LinkedHashMap<>();
        List<NormalizedUser> normalized = new ArrayList<>();
        List<NormalizationIssue> issues = new ArrayList<>();
        int duplicateCount = 0;

        for (RawRecord record : records) {
            String userId = normalizeId(record.fields().get("user_id"));
            if (userId == null) {
                normalized.add(new NormalizedUser(
                        record.logicalFileName(),
                        record.sourceRowNumber(),
                        null,
                        record.fields(),
                        NormalizationStatus.REJECTED_REQUIRED_FIELD,
                        Set.of("missing_user_id")));
                issues.add(issue(record, "MISSING_USER_ID", "user_id is required"));
                continue;
            }
            grouped.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(record);
        }

        for (Map.Entry<String, List<RawRecord>> entry : grouped.entrySet()) {
            List<RawRecord> group = entry.getValue();
            boolean duplicate = group.size() > 1;
            if (duplicate) {
                duplicateCount += group.size() - 1;
            }
            for (int index = 0; index < group.size(); index++) {
                RawRecord record = group.get(index);
                Set<String> flags = duplicate
                        ? Set.of("duplicate_user_id")
                        : Set.of();
                NormalizationStatus status = duplicate
                        ? NormalizationStatus.NORMALIZED_WITH_WARNINGS
                        : NormalizationStatus.NORMALIZED;
                normalized.add(new NormalizedUser(
                        record.logicalFileName(),
                        record.sourceRowNumber(),
                        entry.getKey(),
                        record.fields(),
                        status,
                        flags));
            }
            if (duplicate && !sameFields(group)) {
                issues.add(issue(group.get(0), "CONFLICTING_USER_RECORD",
                        "duplicate user_id has conflicting fields"));
            }
        }
        return new UserNormalization(
                normalized,
                new LinkedHashSet<>(grouped.keySet()),
                duplicateCount,
                issues);
    }

    private RelationshipNormalization normalizeRelationships(
            List<RawRecord> records, Set<String> userIds) {
        List<NormalizedRelationship> normalized = new ArrayList<>();
        List<NormalizationIssue> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> externalNodes = new LinkedHashSet<>();
        int candidateCount = 0;
        int duplicateCount = 0;
        int selfLoopCount = 0;

        for (RawRecord record : records) {
            List<String> targets;
            boolean aggregated = record.fields().containsKey("following_list");
            if (aggregated) {
                try {
                    targets = SocialNetworkFileReader.parseFollowingList(
                            record.fields().get("following_list"));
                } catch (IllegalArgumentException exception) {
                    issues.add(issue(record, "INVALID_FOLLOWING_LIST", exception.getMessage()));
                    continue;
                }
            } else {
                targets = List.of(record.fields().get("target_user_id"));
            }
            String source = normalizeId(record.fields().get("source_user_id"));
            for (int index = 0; index < targets.size(); index++) {
                candidateCount++;
                String target = normalizeId(targets.get(index));
                int arrayIndex = aggregated ? index : -1;
                AssociationStatus sourceStatus = userIds.contains(source)
                        ? AssociationStatus.LINKED
                        : AssociationStatus.EXTERNAL_NODE;
                AssociationStatus targetStatus = userIds.contains(target)
                        ? AssociationStatus.LINKED
                        : AssociationStatus.EXTERNAL_NODE;
                if (source != null && !userIds.contains(source)) {
                    externalNodes.add(source);
                }
                if (target != null && !userIds.contains(target)) {
                    externalNodes.add(target);
                }

                if (source == null || target == null) {
                    normalized.add(new NormalizedRelationship(
                            record.logicalFileName(),
                            record.sourceRowNumber(),
                            arrayIndex,
                            source,
                            target,
                            sourceStatus,
                            targetStatus,
                            RelationStatus.INVALID,
                            NormalizationStatus.REJECTED_REQUIRED_FIELD,
                            false));
                    issues.add(issue(record, "MISSING_RELATION_ENDPOINT",
                            "source_user_id and target_user_id are required"));
                    continue;
                }

                if (source.equals(target)) {
                    selfLoopCount++;
                    normalized.add(new NormalizedRelationship(
                            record.logicalFileName(),
                            record.sourceRowNumber(),
                            arrayIndex,
                            source,
                            target,
                            sourceStatus,
                            targetStatus,
                            RelationStatus.SELF_LOOP,
                            NormalizationStatus.NORMALIZED_WITH_WARNINGS,
                            false));
                    continue;
                }

                String key = source + "\u0000" + target;
                if (!seen.add(key)) {
                    duplicateCount++;
                    normalized.add(new NormalizedRelationship(
                            record.logicalFileName(),
                            record.sourceRowNumber(),
                            arrayIndex,
                            source,
                            target,
                            sourceStatus,
                            targetStatus,
                            RelationStatus.DUPLICATE,
                            NormalizationStatus.NORMALIZED_WITH_WARNINGS,
                            false));
                    continue;
                }
                normalized.add(new NormalizedRelationship(
                        record.logicalFileName(),
                        record.sourceRowNumber(),
                        arrayIndex,
                        source,
                        target,
                        sourceStatus,
                        targetStatus,
                        RelationStatus.VALID,
                        (sourceStatus == AssociationStatus.EXTERNAL_NODE
                                || targetStatus == AssociationStatus.EXTERNAL_NODE)
                                ? NormalizationStatus.NORMALIZED_WITH_WARNINGS
                                : NormalizationStatus.NORMALIZED,
                        true));
            }
        }
        return new RelationshipNormalization(
                normalized, candidateCount, duplicateCount, selfLoopCount, externalNodes, issues);
    }

    private TweetNormalization normalizeTweets(List<RawRecord> records, Set<String> userIds) {
        Map<String, List<RawRecord>> grouped = new LinkedHashMap<>();
        List<NormalizedTweet> normalized = new ArrayList<>();
        List<NormalizationIssue> issues = new ArrayList<>();
        int linkedCount = 0;
        int unlinkedCount = 0;
        int unassignedCount = 0;
        int duplicateCount = 0;
        int conflictDuplicateCount = 0;

        for (RawRecord record : records) {
            String tweetId = normalizeId(record.fields().get("tweet_id"));
            if (tweetId == null) {
                normalized.add(tweet(record, null, userIds, DedupStatus.NOT_APPLICABLE,
                        NormalizationStatus.REJECTED_REQUIRED_FIELD, false, Set.of("missing_tweet_id")));
                issues.add(issue(record, "MISSING_TWEET_ID", "tweet_id is required"));
                continue;
            }
            grouped.computeIfAbsent(tweetId, ignored -> new ArrayList<>()).add(record);
        }

        for (Map.Entry<String, List<RawRecord>> entry : grouped.entrySet()) {
            String tweetId = entry.getKey();
            List<RawRecord> group = entry.getValue();
            if (group.size() == 1) {
                NormalizedTweet value = tweet(
                        group.get(0), tweetId, userIds, DedupStatus.UNIQUE,
                        NormalizationStatus.NORMALIZED, true, Set.of());
                normalized.add(value);
                switch (value.authorStatus()) {
                    case LINKED -> linkedCount++;
                    case UNLINKED_AUTHOR -> unlinkedCount++;
                    case UNASSIGNED_SOURCE -> unassignedCount++;
                    default -> {
                    }
                }
                continue;
            }

            if (sameFields(group)) {
                duplicateCount += group.size() - 1;
                for (int index = 0; index < group.size(); index++) {
                    DedupStatus dedupStatus = index == 0
                            ? DedupStatus.DUPLICATE_PRESERVED_RAW
                            : DedupStatus.DUPLICATE_EXCLUDED_ANALYSIS;
                    normalized.add(tweet(
                            group.get(index), tweetId, userIds, dedupStatus,
                            NormalizationStatus.NORMALIZED_WITH_WARNINGS,
                            index == 0, Set.of("duplicate_tweet_id")));
                }
                NormalizedTweet first = normalized.get(normalized.size() - group.size());
                switch (first.authorStatus()) {
                    case LINKED -> linkedCount++;
                    case UNLINKED_AUTHOR -> unlinkedCount++;
                    case UNASSIGNED_SOURCE -> unassignedCount++;
                    default -> {
                    }
                }
            } else {
                duplicateCount += group.size() - 1;
                conflictDuplicateCount += group.size();
                issues.add(issue(group.get(0), "CONFLICTING_TWEET_RECORD",
                        "duplicate tweet_id has conflicting fields"));
                for (RawRecord record : group) {
                    NormalizedTweet value = tweet(
                            record, tweetId, userIds, DedupStatus.CONFLICT_DUPLICATE,
                            NormalizationStatus.NORMALIZED_WITH_WARNINGS, false,
                            Set.of("conflict_duplicate"));
                    normalized.add(value);
                    switch (value.authorStatus()) {
                        case LINKED -> linkedCount++;
                        case UNLINKED_AUTHOR -> unlinkedCount++;
                        case UNASSIGNED_SOURCE -> unassignedCount++;
                        default -> {
                        }
                    }
                }
            }
        }
        return new TweetNormalization(
                normalized,
                grouped.size(),
                duplicateCount,
                conflictDuplicateCount,
                linkedCount,
                unlinkedCount,
                unassignedCount,
                issues);
    }

    private NormalizedTweet tweet(
            RawRecord record,
            String tweetId,
            Set<String> userIds,
            DedupStatus dedupStatus,
            NormalizationStatus normalizationStatus,
            boolean analysisIncluded,
            Set<String> flags) {
        String sourceFolder = normalizeId(record.fields().get("source_folder"));
        String candidateAuthor = normalizeId(record.fields().get("user_id"));
        if (candidateAuthor == null) {
            candidateAuthor = sourceFolder;
        }
        AssociationStatus authorStatus;
        String authorUserId = candidateAuthor;
        if (candidateAuthor == null) {
            authorStatus = AssociationStatus.UNLINKED_AUTHOR;
        } else if ("Arab_API".equalsIgnoreCase(candidateAuthor)) {
            authorStatus = AssociationStatus.UNASSIGNED_SOURCE;
            authorUserId = null;
        } else if (userIds.contains(candidateAuthor)) {
            authorStatus = AssociationStatus.LINKED;
        } else {
            authorStatus = AssociationStatus.UNLINKED_AUTHOR;
        }
        Set<String> actualFlags = new LinkedHashSet<>(flags);
        if (authorStatus == AssociationStatus.UNASSIGNED_SOURCE) {
            actualFlags.add("unassigned_source");
        } else if (authorStatus == AssociationStatus.UNLINKED_AUTHOR) {
            actualFlags.add("unlinked_author");
        }
        NormalizationStatus actualStatus = authorStatus == AssociationStatus.LINKED
                ? normalizationStatus
                : NormalizationStatus.NORMALIZED_WITH_WARNINGS;
        return new NormalizedTweet(
                record.logicalFileName(),
                record.sourceRowNumber(),
                tweetId,
                authorUserId,
                sourceFolder,
                record.fields(),
                authorStatus,
                dedupStatus,
                actualStatus,
                analysisIncluded,
                actualFlags);
    }

    private static boolean sameFields(List<RawRecord> records) {
        Map<String, String> first = records.get(0).fields();
        return records.stream().allMatch(record -> first.equals(record.fields()));
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static NormalizationIssue issue(RawRecord record, String code, String message) {
        return new NormalizationIssue(record.logicalFileName(), record.sourceRowNumber(), code, message);
    }

    private record UserNormalization(
            List<NormalizedUser> records,
            Set<String> userIds,
            int duplicateCount,
            List<NormalizationIssue> issues) {
    }

    private record RelationshipNormalization(
            List<NormalizedRelationship> records,
            int candidateCount,
            int duplicateCount,
            int selfLoopCount,
            Set<String> externalNodes,
            List<NormalizationIssue> issues) {
    }

    private record TweetNormalization(
            List<NormalizedTweet> records,
            int uniqueTweetIdCount,
            int duplicateCount,
            int conflictDuplicateCount,
            int linkedCount,
            int unlinkedCount,
            int unassignedCount,
            List<NormalizationIssue> issues) {
    }
}
