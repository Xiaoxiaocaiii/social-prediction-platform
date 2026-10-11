package com.prediction.socialnetwork.reader;

public record FileReadIssue(int sourceRowNumber, String code, String message) {

    public FileReadIssue {
        if (sourceRowNumber < 0) {
            throw new IllegalArgumentException("sourceRowNumber must not be negative");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
