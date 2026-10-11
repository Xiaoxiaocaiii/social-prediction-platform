package com.prediction.socialnetwork.reader;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RawRecord(
        String logicalFileName,
        int sourceRowNumber,
        Map<String, String> fields,
        FileReadStatus status) {

    public RawRecord {
        logicalFileName = requireText(logicalFileName, "logicalFileName");
        if (sourceRowNumber < 1) {
            throw new IllegalArgumentException("sourceRowNumber must be positive");
        }
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(fields, "fields")));
        status = Objects.requireNonNull(status, "status");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
