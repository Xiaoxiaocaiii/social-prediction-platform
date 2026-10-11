package com.prediction.socialnetwork.quality;

import com.prediction.socialnetwork.domain.DatasetStatus;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.normalization.AssociationStatus;
import com.prediction.socialnetwork.normalization.DedupStatus;
import com.prediction.socialnetwork.normalization.NormalizationIssue;
import com.prediction.socialnetwork.normalization.NormalizationStatus;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.normalization.NormalizedTweet;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.RelationStatus;
import com.prediction.socialnetwork.normalization.SocialNetworkNormalizationResult;
import com.prediction.socialnetwork.reader.FileReadIssue;
import com.prediction.socialnetwork.reader.FileReadReport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SocialNetworkQualityReportGenerator {

    public SocialNetworkQualityReport generate(
            String datasetId,
            String datasetVersion,
            DatasetType datasetType,
            List<FileReadReport> fileReports,
            SocialNetworkNormalizationResult normalized) {
        List<QualityIssue> issues = new ArrayList<>();
        issues.addAll(fileIssues(fileReports));
        issues.addAll(normalizationIssues(normalized.report().issues()));

        Map<String, Integer> users = userQuality(normalized.users(), normalized);
        Map<String, Integer> relationships = relationshipQuality(normalized.relationships(), normalized);
        Map<String, Integer> tweets = tweetQuality(normalized.tweets(), normalized);
        Map<String, Integer> interactions = interactionQuality(normalized.tweets());
        Map<String, Integer> text = textQuality(normalized.tweets());
        Map<String, Double> coverage = coverage(users, relationships, tweets, text);
        Map<String, String> capabilityImpact = capabilityImpact(
                users, relationships, tweets, interactions, text);
        DatasetStatus status = determineStatus(
                fileReports, normalized, issues, users, relationships, tweets, interactions);

        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("file_count", fileReports.size());
        summary.put("user_record_count", normalized.report().rawUserCount());
        summary.put("relationship_record_count", normalized.report().rawRelationshipRecordCount());
        summary.put("relationship_candidate_count", normalized.report().candidateRelationshipCount());
        summary.put("tweet_record_count", normalized.report().rawTweetCount());
        summary.put("quality_issue_count", issues.size());
        summary.put("capability_impact_count", capabilityImpact.size());

        return new SocialNetworkQualityReport(
                datasetId,
                datasetVersion,
                datasetType,
                status,
                Instant.now(),
                summary,
                users,
                relationships,
                tweets,
                interactions,
                text,
                coverage,
                fileReports,
                issues,
                capabilityImpact);
    }

    private Map<String, Integer> userQuality(
            List<NormalizedUser> users, SocialNetworkNormalizationResult result) {
        Map<String, Integer> values = new LinkedHashMap<>();
        int normalized = (int) users.stream()
                .filter(user -> user.normalizationStatus() != NormalizationStatus.REJECTED_REQUIRED_FIELD
                        && user.normalizationStatus() != NormalizationStatus.REJECTED_INVALID_FORMAT)
                .count();
        values.put("raw_user_count", result.report().rawUserCount());
        values.put("normalized_user_count", normalized);
        values.put("unique_user_id_count", result.report().uniqueUserIdCount());
        values.put("duplicate_user_id_count", result.report().duplicateUserCount());
        values.put("missing_user_id_count", countFlag(users, "missing_user_id"));
        values.put("missing_username_count", countBlank(users, "username"));
        values.put("missing_display_name_count", countBlank(users, "display_name"));
        values.put("missing_bio_count", countBlank(users, "bio"));
        values.put("invalid_counter_count", countInvalidCounters(users));
        values.put("profile_missing_count", countBlank(users, "user_id"));
        return values;
    }

    private Map<String, Integer> relationshipQuality(
            List<NormalizedRelationship> relationships, SocialNetworkNormalizationResult result) {
        Map<String, Integer> values = new LinkedHashMap<>();
        Set<String> externalSources = new HashSet<>();
        Set<String> externalTargets = new HashSet<>();
        int normalized = 0;
        int missingSource = 0;
        int missingTarget = 0;
        int invalid = 0;
        for (NormalizedRelationship relationship : relationships) {
            if (relationship.sourceNodeStatus() == AssociationStatus.EXTERNAL_NODE
                    && relationship.sourceUserId() != null) {
                externalSources.add(relationship.sourceUserId());
            }
            if (relationship.targetNodeStatus() == AssociationStatus.EXTERNAL_NODE
                    && relationship.targetUserId() != null) {
                externalTargets.add(relationship.targetUserId());
            }
            if (relationship.relationStatus() == RelationStatus.VALID
                    && relationship.analysisIncluded()) {
                normalized++;
            }
            if (relationship.sourceUserId() == null) {
                missingSource++;
            }
            if (relationship.targetUserId() == null) {
                missingTarget++;
            }
            if (relationship.relationStatus() == RelationStatus.INVALID) {
                invalid++;
            }
        }
        values.put("raw_relationship_count", result.report().rawRelationshipRecordCount());
        values.put("candidate_relationship_count", result.report().candidateRelationshipCount());
        values.put("normalized_relationship_count", normalized);
        values.put("unique_relationship_count", normalized);
        values.put("duplicate_relationship_count", result.report().duplicateRelationshipCount());
        values.put("self_loop_count", result.report().selfLoopCount());
        values.put("missing_source_count", missingSource);
        values.put("missing_target_count", missingTarget);
        values.put("external_source_node_count", externalSources.size());
        values.put("external_target_node_count", externalTargets.size());
        values.put("invalid_relationship_count", invalid);
        return values;
    }

    private Map<String, Integer> tweetQuality(
            List<NormalizedTweet> tweets, SocialNetworkNormalizationResult result) {
        Map<String, Integer> values = new LinkedHashMap<>();
        int linked = 0;
        int unlinked = 0;
        int unassigned = 0;
        int missingAuthor = 0;
        int emptyText = 0;
        int timeFailed = 0;
        for (NormalizedTweet tweet : tweets) {
            switch (tweet.authorStatus()) {
                case LINKED -> linked++;
                case UNLINKED_AUTHOR -> {
                    unlinked++;
                    if (tweet.authorUserId() == null) {
                        missingAuthor++;
                    }
                }
                case UNASSIGNED_SOURCE -> unassigned++;
                default -> {
                }
            }
            if (isBlank(tweet.fields().get("text"))) {
                emptyText++;
            }
            String createdAt = tweet.fields().get("created_at");
            if (!isBlank(createdAt) && !parseDate(createdAt)) {
                timeFailed++;
            }
        }
        values.put("raw_tweet_count", result.report().rawTweetCount());
        values.put("unique_tweet_id_count", result.report().uniqueTweetIdCount());
        values.put("duplicate_tweet_count", result.report().duplicateTweetCount());
        values.put("conflict_duplicate_count", result.report().conflictDuplicateCount());
        values.put("missing_tweet_id_count", countRejectedTweetIds(tweets));
        values.put("missing_author_count", missingAuthor);
        values.put("linked_author_count", linked);
        values.put("unlinked_author_count", unlinked);
        values.put("unassigned_source_count", unassigned);
        values.put("empty_text_count", emptyText);
        values.put("time_parse_failed_count", timeFailed);
        return values;
    }

    private Map<String, Integer> interactionQuality(List<NormalizedTweet> tweets) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String field : List.of("like_count", "reply_count", "repost_count", "bookmark_count")) {
            int missing = 0;
            int invalid = 0;
            boolean hasValue = false;
            boolean allZero = true;
            for (NormalizedTweet tweet : tweets) {
                String value = tweet.fields().get(field);
                if (isBlank(value)) {
                    missing++;
                } else if (!value.matches("\\d+")) {
                    invalid++;
                } else {
                    hasValue = true;
                    if (!"0".equals(value)) {
                        allZero = false;
                    }
                }
            }
            values.put(field.replace("_count", "_missing_count"), missing);
            values.put(field.replace("_count", "_invalid_count"), invalid);
            if ("bookmark_count".equals(field)) {
                values.put("bookmark_all_zero", hasValue && allZero && invalid == 0 ? 1 : 0);
            }
        }
        return values;
    }

    private Map<String, Integer> textQuality(List<NormalizedTweet> tweets) {
        Map<String, Integer> values = new LinkedHashMap<>();
        int valid = 0;
        int empty = 0;
        for (NormalizedTweet tweet : tweets) {
            if (isBlank(tweet.fields().get("text"))) {
                empty++;
            } else {
                valid++;
            }
        }
        values.put("valid_text_count", valid);
        values.put("empty_text_count", empty);
        values.put("language_detected_count", 0);
        values.put("language_unknown_count", valid);
        values.put("mixed_language_count", 0);
        values.put("text_analysis_available", valid > 0 ? 1 : 0);
        return values;
    }

    private Map<String, Double> coverage(
            Map<String, Integer> users,
            Map<String, Integer> relationships,
            Map<String, Integer> tweets,
            Map<String, Integer> text) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("user_id_coverage", ratio(
                users.get("normalized_user_count"), users.get("raw_user_count")));
        values.put("relationship_inclusion_rate", ratio(
                relationships.get("normalized_relationship_count"),
                relationships.get("candidate_relationship_count")));
        values.put("tweet_author_link_rate", ratio(
                tweets.get("linked_author_count"), tweets.get("raw_tweet_count")));
        values.put("valid_text_rate", ratio(
                text.get("valid_text_count"), tweets.get("raw_tweet_count")));
        return values;
    }

    private Map<String, String> capabilityImpact(
            Map<String, Integer> users,
            Map<String, Integer> relationships,
            Map<String, Integer> tweets,
            Map<String, Integer> interactions,
            Map<String, Integer> text) {
        Map<String, String> values = new LinkedHashMap<>();
        if (relationships.get("candidate_relationship_count") == 0) {
            values.put("network", "unavailable: no valid relationship candidates");
        }
        if (users.get("normalized_user_count") == 0) {
            values.put("user_profile", "unavailable: no normalized users");
        }
        if (tweets.get("raw_tweet_count") == 0) {
            values.put("tweet_interaction", "unavailable: no tweet records");
            values.put("text_analysis", "unavailable: no tweet records");
        }
        if (tweets.get("unassigned_source_count") > 0
                || tweets.get("unlinked_author_count") > 0) {
            values.put("user_tweet_aggregation", "limited: some authors are not linked");
        }
        if (interactions.getOrDefault("bookmark_all_zero", 0) == 1) {
            values.put("bookmark_analysis", "disabled: no effective bookmark variation");
        }
        if (tweets.get("time_parse_failed_count") > 0) {
            values.put("time_distribution", "limited: some timestamps are unparseable");
        }
        if (text.get("valid_text_count") == 0 && tweets.get("raw_tweet_count") > 0) {
            values.put("text_analysis", "unavailable: no valid text samples");
        }
        return values;
    }

    private DatasetStatus determineStatus(
            List<FileReadReport> fileReports,
            SocialNetworkNormalizationResult normalized,
            List<QualityIssue> issues,
            Map<String, Integer> users,
            Map<String, Integer> relationships,
            Map<String, Integer> tweets,
            Map<String, Integer> interactions) {
        boolean blocking = fileReports.stream()
                .anyMatch(report -> report.headerStatus().name().equals("INVALID")
                        || (report.readErrorCount() > 0 && report.readOkCount() == 0));
        if (blocking) {
            return DatasetStatus.UNAVAILABLE;
        }
        boolean partial = users.get("normalized_user_count") == 0
                || (relationships.get("candidate_relationship_count") == 0
                && tweets.get("raw_tweet_count") == 0);
        if (partial) {
            return DatasetStatus.PARTIAL;
        }
        boolean warning = !issues.isEmpty()
                || normalized.report().duplicateTweetCount() > 0
                || normalized.report().conflictDuplicateCount() > 0
                || normalized.report().unassignedSourceCount() > 0
                || normalized.report().selfLoopCount() > 0
                || normalized.report().duplicateRelationshipCount() > 0
                || interactions.getOrDefault("bookmark_all_zero", 0) == 1;
        return warning ? DatasetStatus.AVAILABLE_WITH_WARNINGS : DatasetStatus.HEALTHY;
    }

    private List<QualityIssue> fileIssues(List<FileReadReport> reports) {
        List<QualityIssue> issues = new ArrayList<>();
        for (FileReadReport report : reports) {
            for (FileReadIssue issue : report.issues()) {
                QualitySeverity severity = issue.code().contains("HEADER")
                        || issue.code().contains("OPEN")
                        ? QualitySeverity.BLOCKING
                        : QualitySeverity.ERROR;
                issues.add(new QualityIssue(
                        issue.code(),
                        severity,
                        "file",
                        issue.message(),
                        report.logicalFileName(),
                        issue.sourceRowNumber() > 0
                                ? List.of(issue.sourceRowNumber())
                                : List.of()));
            }
        }
        return issues;
    }

    private List<QualityIssue> normalizationIssues(List<NormalizationIssue> sourceIssues) {
        List<QualityIssue> issues = new ArrayList<>();
        for (NormalizationIssue issue : sourceIssues) {
            QualitySeverity severity = issue.code().contains("MISSING")
                    ? QualitySeverity.ERROR
                    : QualitySeverity.WARNING;
            issues.add(new QualityIssue(
                    issue.code(),
                    severity,
                    issue.code().contains("RELATION") ? "relationship" : "tweet",
                    issue.message(),
                    issue.sourceFileName(),
                    List.of(issue.sourceRowNumber())));
        }
        return issues;
    }

    private static int countFlag(List<NormalizedUser> users, String flag) {
        return (int) users.stream().filter(user -> user.qualityFlags().contains(flag)).count();
    }

    private static int countBlank(List<NormalizedUser> users, String field) {
        return (int) users.stream().filter(user -> isBlank(user.fields().get(field))).count();
    }

    private static int countInvalidCounters(List<NormalizedUser> users) {
        int count = 0;
        for (NormalizedUser user : users) {
            for (String field : List.of("followers_count", "following_count", "tweet_count")) {
                String value = user.fields().get(field);
                if (!isBlank(value) && !value.matches("\\d+")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countRejectedTweetIds(List<NormalizedTweet> tweets) {
        return (int) tweets.stream()
                .filter(tweet -> tweet.normalizationStatus() == NormalizationStatus.REJECTED_REQUIRED_FIELD)
                .count();
    }

    private static boolean parseDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
            try {
                LocalDateTime.parse(value);
                return true;
            } catch (DateTimeParseException ignoredAgain) {
                return false;
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static double ratio(Integer numerator, Integer denominator) {
        if (denominator == null || denominator == 0) {
            return 0.0;
        }
        return numerator.doubleValue() / denominator.doubleValue();
    }
}
