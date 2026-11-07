package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.domain.DatasetFileRole;
import java.nio.file.Path;
import java.util.Objects;

public record SocialNetworkUploadFileInput(
        String partId,
        String fileName,
        DatasetFileRole role,
        Path stagedPath) {

    public SocialNetworkUploadFileInput {
        partId = requireText(partId, "partId");
        fileName = requireText(fileName, "fileName");
        role = Objects.requireNonNull(role, "role");
        stagedPath = Objects.requireNonNull(stagedPath, "stagedPath");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
