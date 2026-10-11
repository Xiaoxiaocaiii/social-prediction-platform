package com.prediction.socialnetwork.domain;

import java.time.Instant;
import java.util.Objects;

public record UploadSession(
        String sessionId,
        String datasetId,
        Instant createdAt,
        Instant lastAccessedAt,
        Instant idleExpiresAt,
        Instant hardExpiresAt,
        SessionStatus status) {

    public enum SessionStatus {
        ACTIVE,
        EXPIRED,
        CLOSED,
        FAILED
    }

    public UploadSession {
        sessionId = requireText(sessionId, "sessionId");
        datasetId = requireText(datasetId, "datasetId");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        lastAccessedAt = Objects.requireNonNull(lastAccessedAt, "lastAccessedAt");
        idleExpiresAt = Objects.requireNonNull(idleExpiresAt, "idleExpiresAt");
        hardExpiresAt = Objects.requireNonNull(hardExpiresAt, "hardExpiresAt");
        status = Objects.requireNonNull(status, "status");
    }

    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return status != SessionStatus.ACTIVE
                || !now.isBefore(idleExpiresAt)
                || !now.isBefore(hardExpiresAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
