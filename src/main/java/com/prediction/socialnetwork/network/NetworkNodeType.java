package com.prediction.socialnetwork.network;

public enum NetworkNodeType {
    PROFILE("profile"),
    EXTERNAL("external");

    private final String value;

    NetworkNodeType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
