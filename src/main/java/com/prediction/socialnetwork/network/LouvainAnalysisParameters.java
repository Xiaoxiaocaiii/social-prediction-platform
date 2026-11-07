package com.prediction.socialnetwork.network;

public record LouvainAnalysisParameters(
        double resolution,
        NodeOrder nodeOrder,
        RelationProjection relationProjection) {

    public static final double MIN_SENSITIVITY_RESOLUTION = 0.5;
    public static final double MAX_SENSITIVITY_RESOLUTION = 2.0;
    public static final LouvainAnalysisParameters DEFAULT =
            new LouvainAnalysisParameters(
                    LouvainCommunityDetector.RESOLUTION,
                    NodeOrder.DEGREE_DESCENDING_THEN_ID_ASCENDING,
                    RelationProjection.ANY_DIRECTION);

    public LouvainAnalysisParameters {
        if (!Double.isFinite(resolution)
                || resolution < MIN_SENSITIVITY_RESOLUTION
                || resolution > MAX_SENSITIVITY_RESOLUTION) {
            throw new IllegalArgumentException(
                    "resolution must be finite and within the CL12 sensitivity range");
        }
        if (nodeOrder == null || relationProjection == null) {
            throw new IllegalArgumentException("Louvain parameters must not be null");
        }
    }

    public enum NodeOrder {
        DEGREE_DESCENDING_THEN_ID_ASCENDING,
        DEGREE_ASCENDING_THEN_ID_ASCENDING
    }

    public enum RelationProjection {
        ANY_DIRECTION("undirected_simple"),
        MUTUAL_ONLY("undirected_mutual");

        private final String resultName;

        RelationProjection(String resultName) {
            this.resultName = resultName;
        }

        public String resultName() {
            return resultName;
        }
    }
}
