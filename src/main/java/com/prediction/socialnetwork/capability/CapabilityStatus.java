package com.prediction.socialnetwork.capability;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CapabilityStatus {
    AVAILABLE("available"),
    LIMITED("limited"),
    DISABLED("disabled"),
    UNAVAILABLE("unavailable"),
    CHECKING("checking");

    private final String value;

    CapabilityStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
