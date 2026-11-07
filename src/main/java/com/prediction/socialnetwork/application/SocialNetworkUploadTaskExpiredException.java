package com.prediction.socialnetwork.application;

public class SocialNetworkUploadTaskExpiredException extends RuntimeException {

    public SocialNetworkUploadTaskExpiredException() {
        super("upload task record has expired");
    }
}
