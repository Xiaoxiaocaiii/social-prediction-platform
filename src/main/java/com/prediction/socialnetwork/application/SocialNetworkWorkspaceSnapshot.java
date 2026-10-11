package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.capability.SocialNetworkCapabilityMatrix;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.quality.SocialNetworkQualityReport;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import com.prediction.socialnetwork.normalization.NormalizedTweet;
import com.prediction.socialnetwork.normalization.NormalizedRelationship;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import com.prediction.socialnetwork.text.SocialNetworkTextAnalysis;
import java.util.List;

public record SocialNetworkWorkspaceSnapshot(
        SocialNetworkDataset dataset,
        SocialNetworkQualityReport quality,
        SocialNetworkCapabilityMatrix capabilities,
        SocialNetworkGraphResult network,
        SocialNetworkInteractionScale interactions,
        SocialNetworkTextAnalysis.Result textAnalysis,
        List<NormalizedUser> users,
        List<NormalizedRelationship> relationships,
        List<NormalizedTweet> tweets) {

    public SocialNetworkWorkspaceSnapshot {
        users = List.copyOf(users);
        relationships = List.copyOf(relationships);
        tweets = List.copyOf(tweets);
    }
}
