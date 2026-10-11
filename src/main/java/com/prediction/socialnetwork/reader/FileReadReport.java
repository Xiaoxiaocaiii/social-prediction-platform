package com.prediction.socialnetwork.reader;

import com.prediction.socialnetwork.domain.SourceFormat;
import java.util.List;

public record FileReadReport(
        String logicalFileName,
        SourceFormat format,
        String encoding,
        HeaderStatus headerStatus,
        int rowCount,
        int readOkCount,
        int readWarningCount,
        int readErrorCount,
        int readSkippedCount,
        List<FileReadIssue> issues) {

    public FileReadReport {
        issues = List.copyOf(issues);
    }

    public boolean isUsable() {
        return headerStatus == HeaderStatus.VALID && readOkCount + readWarningCount > 0;
    }
}
