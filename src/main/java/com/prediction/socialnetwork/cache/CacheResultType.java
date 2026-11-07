package com.prediction.socialnetwork.cache;

public enum CacheResultType {
    DATASET_METADATA("dataset-metadata"),
    QUALITY_REPORT("quality-report"),
    CAPABILITY_MATRIX("capability-matrix"),
    NETWORK_SUMMARY("network-summary"),
    NETWORK_EVIDENCE_SUMMARY("network-evidence-summary"),
    NETWORK_GRAPH("network-graph"),
    CLIQUE_ANALYSIS("clique-analysis"),
    LOUVAIN_ANALYSIS("louvain-analysis"),
    NETWORK_RANKING("network-ranking"),
    USER_DETAIL("user-detail"),
    TWEET_LIST("tweet-list"),
    INTERACTION_SUMMARY("interaction-summary"),
    TOPIC_ANALYSIS("topic-analysis"),
    EXPORT("export");

    private final String value;

    CacheResultType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
