package com.prediction.socialnetwork.export;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Serializes already-scoped records as RFC 4180 compatible UTF-8 CSV text.
 */
public final class SocialNetworkCsvSerializer {

    private static final String UTF_8_BOM = "\uFEFF";

    public String serialize(
            List<String> columns,
            List<? extends Map<String, ?>> rows) {
        Objects.requireNonNull(columns, "columns");
        Objects.requireNonNull(rows, "rows");
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        StringBuilder csv = new StringBuilder(UTF_8_BOM);
        csv.append(row(columns));
        for (Map<String, ?> source : rows) {
            Objects.requireNonNull(source, "row");
            csv.append(row(columns.stream()
                    .map(column -> source.get(column))
                    .toList()));
        }
        return csv.toString();
    }

    private static String row(List<?> values) {
        StringJoiner joiner = new StringJoiner(",");
        for (Object value : values) {
            joiner.add(escape(value == null ? "" : String.valueOf(value)));
        }
        return joiner + "\r\n";
    }

    private static String escape(String value) {
        if (value.indexOf('"') < 0
                && value.indexOf(',') < 0
                && value.indexOf('\r') < 0
                && value.indexOf('\n') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
