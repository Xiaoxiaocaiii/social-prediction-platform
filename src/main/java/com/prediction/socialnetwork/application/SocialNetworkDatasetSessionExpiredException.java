package com.prediction.socialnetwork.application;

public class SocialNetworkDatasetSessionExpiredException extends IllegalStateException {

    public SocialNetworkDatasetSessionExpiredException() {
        super("dataset session has expired");
    }
}
