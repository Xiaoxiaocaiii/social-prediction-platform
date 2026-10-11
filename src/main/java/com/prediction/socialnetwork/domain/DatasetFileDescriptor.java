package com.prediction.socialnetwork.domain;

import java.util.Objects;

public record DatasetFileDescriptor(
        String logicalFileName,
        DatasetFileRole role,
        SourceFormat format,
        String encoding) {

    public DatasetFileDescriptor {
        logicalFileName = requireText(logicalFileName, "logicalFileName");
        role = Objects.requireNonNull(role, "role");
        format = Objects.requireNonNull(format, "format");
        encoding = requireText(encoding, "encoding");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
