package com.prediction.socialnetwork.quality;

import com.prediction.socialnetwork.domain.DatasetStatus;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.reader.FileReadReport;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SocialNetworkQualityReport(
        String datasetId,
        String datasetVersion,
        DatasetType datasetType,
        DatasetStatus datasetStatus,
        Instant generatedAt,
        Map<String, Integer> summary,
        Map<String, Integer> userQuality,
        Map<String, Integer> relationshipQuality,
        Map<String, Integer> tweetQuality,
        Map<String, Integer> interactionQuality,
        Map<String, Integer> textQuality,
        Map<String, Double> coverage,
        List<FileReadReport> fileReports,
        List<QualityIssue> issues,
        Map<String, String> capabilityImpact) {

    public SocialNetworkQualityReport {
        summary = Map.copyOf(summary);
        userQuality = Map.copyOf(userQuality);
        relationshipQuality = Map.copyOf(relationshipQuality);
        tweetQuality = Map.copyOf(tweetQuality);
        interactionQuality = Map.copyOf(interactionQuality);
        textQuality = Map.copyOf(textQuality);
        coverage = Map.copyOf(coverage);
        fileReports = List.copyOf(fileReports);
        issues = List.copyOf(issues);
        capabilityImpact = Map.copyOf(capabilityImpact);
    }
}
