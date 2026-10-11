package com.prediction.socialnetwork.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Canonical state vocabulary shared by social-network capabilities and views.
 */
public enum SocialNetworkState {
    IDLE("idle"),
    LOADING("loading"),
    AVAILABLE("available"),
    EMPTY("empty"),
    LIMITED("limited"),
    FAILED("failed"),
    DISABLED("disabled"),
    UNAVAILABLE("unavailable");

    private final String value;

    SocialNetworkState(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static SocialNetworkState fromValue(String value) {
        return Arrays.stream(values())
                .filter(state -> state.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unsupported social network state: " + value));
    }
}
