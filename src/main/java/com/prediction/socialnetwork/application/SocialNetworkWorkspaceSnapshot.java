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
import java.util.Objects;
import java.util.function.Supplier;

public record SocialNetworkWorkspaceSnapshot(
        SocialNetworkDataset dataset,
        SocialNetworkQualityReport quality,
        SocialNetworkCapabilityMatrix capabilities,
        SocialNetworkGraphResult network,
        SocialNetworkInteractionScale interactions,
        Supplier<SocialNetworkTextAnalysis.Result> textAnalysisProvider,
        List<NormalizedUser> users,
        List<NormalizedRelationship> relationships,
        List<NormalizedTweet> tweets,
        SocialNetworkDataFingerprintService.Fingerprint inputFingerprint) {

    public SocialNetworkWorkspaceSnapshot {
        textAnalysisProvider = memoize(textAnalysisProvider);
        users = List.copyOf(users);
        relationships = List.copyOf(relationships);
        tweets = List.copyOf(tweets);
    }

    public SocialNetworkWorkspaceSnapshot(
            SocialNetworkDataset dataset,
            SocialNetworkQualityReport quality,
            SocialNetworkCapabilityMatrix capabilities,
            SocialNetworkGraphResult network,
            SocialNetworkInteractionScale interactions,
            Supplier<SocialNetworkTextAnalysis.Result> textAnalysisProvider,
            List<NormalizedUser> users,
            List<NormalizedRelationship> relationships,
            List<NormalizedTweet> tweets) {
        this(
                dataset,
                quality,
                capabilities,
                network,
                interactions,
                textAnalysisProvider,
                users,
                relationships,
                tweets,
                null);
    }

    public SocialNetworkTextAnalysis.Result textAnalysis() {
        return textAnalysisProvider.get();
    }

    public GraphAnalysisInput graphAnalysisInput() {
        return new GraphAnalysisInput(network, users, relationships, inputFingerprint);
    }

    public SocialNetworkWorkspaceSnapshot withInputFingerprint(
            SocialNetworkDataFingerprintService.Fingerprint fingerprint) {
        return new SocialNetworkWorkspaceSnapshot(
                dataset,
                quality,
                capabilities,
                network,
                interactions,
                this::textAnalysis,
                users,
                relationships,
                tweets,
                Objects.requireNonNull(fingerprint, "fingerprint"));
    }

    public record GraphAnalysisInput(
            SocialNetworkGraphResult network,
            List<NormalizedUser> users,
            List<NormalizedRelationship> relationships,
            SocialNetworkDataFingerprintService.Fingerprint inputFingerprint) {

        public GraphAnalysisInput {
            users = List.copyOf(users);
            relationships = List.copyOf(relationships);
        }
    }

    private static Supplier<SocialNetworkTextAnalysis.Result> memoize(
            Supplier<SocialNetworkTextAnalysis.Result> source) {
        Objects.requireNonNull(source, "textAnalysisProvider");
        return new Supplier<>() {
            private volatile SocialNetworkTextAnalysis.Result cached;

            @Override
            public SocialNetworkTextAnalysis.Result get() {
                SocialNetworkTextAnalysis.Result result = cached;
                if (result == null) {
                    synchronized (this) {
                        result = cached;
                        if (result == null) {
                            result = Objects.requireNonNull(source.get(),
                                    "text analysis result");
                            cached = result;
                        }
                    }
                }
                return result;
            }
        };
    }
}
