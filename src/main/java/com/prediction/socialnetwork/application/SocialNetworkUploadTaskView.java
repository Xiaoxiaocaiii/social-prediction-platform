package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.capability.SocialNetworkCapabilityMatrix;
import com.prediction.socialnetwork.domain.DatasetFileRole;
import com.prediction.socialnetwork.domain.SourceFormat;
import com.prediction.socialnetwork.quality.SocialNetworkQualityReport;
import java.time.Instant;
import java.util.List;

public record SocialNetworkUploadTaskView(
        String uploadTaskId,
        String status,
        String phase,
        Instant updatedAt,
        Preview preview,
        Failure error) {

    public record Preview(
            String sessionId,
            String datasetId,
            String datasetVersion,
            String type,
            Instant expiresAt,
            SocialNetworkQualityReport qualitySummary,
            SocialNetworkCapabilityMatrix capabilitySummary,
            List<FileSummary> files) {
        public Preview {
            files = List.copyOf(files);
        }
    }

    public record FileSummary(
            String partId,
            String fileName,
            DatasetFileRole role,
            SourceFormat format,
            int rowCount,
            int readOkCount,
            int readSkippedCount,
            int readErrorCount,
            String qualityStatus) {
    }

    public record Failure(String code, String message, List<Issue> issues) {
        public Failure {
            issues = List.copyOf(issues);
        }
    }

    public record Issue(
            String partId,
            String fileName,
            String issueCode,
            List<Integer> sourceRowNumbers) {
        public Issue {
            sourceRowNumbers = List.copyOf(sourceRowNumbers);
        }
    }
}
