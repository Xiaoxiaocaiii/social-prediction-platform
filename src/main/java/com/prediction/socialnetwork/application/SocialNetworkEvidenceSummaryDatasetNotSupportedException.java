package com.prediction.socialnetwork.application;

public class SocialNetworkEvidenceSummaryDatasetNotSupportedException
        extends RuntimeException {

    public SocialNetworkEvidenceSummaryDatasetNotSupportedException() {
        super("evidence summary currently supports builtin datasets only");
    }
}
