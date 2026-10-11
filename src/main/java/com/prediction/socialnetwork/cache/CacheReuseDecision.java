package com.prediction.socialnetwork.cache;

public record CacheReuseDecision(
        boolean reusable,
        String reasonCode,
        String message) {

    public CacheReuseDecision {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
