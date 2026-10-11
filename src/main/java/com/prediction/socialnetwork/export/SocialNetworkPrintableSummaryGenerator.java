package com.prediction.socialnetwork.export;

import java.util.Map;
import java.util.Objects;

/**
 * Generates a concise printable summary from the same export metadata.
 */
public final class SocialNetworkPrintableSummaryGenerator {

    public String generate(
            SocialNetworkExportMetadata metadata,
            String exportType) {
        Objects.requireNonNull(metadata, "metadata");
        if (exportType == null || exportType.isBlank()) {
            throw new IllegalArgumentException("exportType must not be blank");
        }

        StringBuilder summary = new StringBuilder();
        line(summary, "社交网络分析导出摘要");
        line(summary, "导出类型", exportType);
        line(summary, "数据集", metadata.datasetId());
        line(summary, "数据集版本", metadata.datasetVersion());
        line(summary, "规范化版本", metadata.normalizationVersion());
        line(summary, "指标版本", metadata.metricVersion());
        line(summary, "文本处理版本", metadata.textProcessingVersion());
        line(summary, "解释版本", metadata.explanationVersion());
        line(summary, "生成时间", metadata.exportedAt().toString());
        line(summary, "来源说明", metadata.sourceDescription());
        line(summary, "查询条件", format(metadata.query()));
        line(summary, "筛选范围", format(metadata.scope()));
        line(summary, "样本计数", format(metadata.sampleCounts()));
        summary.append("\n边界说明:\n");
        for (String note : metadata.boundaryNotes()) {
            summary.append("- ").append(note).append('\n');
        }
        return summary.toString();
    }

    private static void line(StringBuilder summary, String label, String value) {
        summary.append(label).append(": ").append(value).append('\n');
    }

    private static void line(StringBuilder summary, String title) {
        summary.append(title).append('\n');
    }

    private static String format(Map<String, Object> values) {
        if (values.isEmpty()) {
            return "无";
        }
        StringBuilder result = new StringBuilder();
        values.forEach((key, value) -> {
            if (result.length() > 0) {
                result.append("; ");
            }
            result.append(key).append('=').append(value);
        });
        return result.toString();
    }
}
