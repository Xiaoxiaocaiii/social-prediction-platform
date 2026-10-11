package com.prediction.socialnetwork.normalization;

import java.util.List;

public record SocialNetworkNormalizationResult(
        List<NormalizedUser> users,
        List<NormalizedRelationship> relationships,
        List<NormalizedTweet> tweets,
        NormalizationReport report) {

    public SocialNetworkNormalizationResult {
        users = List.copyOf(users);
        relationships = List.copyOf(relationships);
        tweets = List.copyOf(tweets);
    }
}
