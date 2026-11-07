package com.prediction.socialnetwork.application;

public class SocialNetworkDatasetContextException extends IllegalArgumentException {

    public SocialNetworkDatasetContextException() {
        super("dataset context does not match");
    }
}
