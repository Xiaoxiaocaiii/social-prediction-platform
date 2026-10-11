package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SocialNetworkMetricCatalogTest {

    @Test
    void providesStableDefinitionsForPageAndExportConsumers() {
        SocialNetworkMetricCatalog.MetricDefinition pageRank =
                SocialNetworkMetricCatalog.require("pagerank");
        SocialNetworkMetricCatalog.MetricDefinition likes =
                SocialNetworkMetricCatalog.require("like_count");

        assertEquals("PageRank", pageRank.label());
        assertEquals("network", pageRank.type());
        assertEquals("结构位置指标，不等同于社会影响力",
                pageRank.scope().substring(pageRank.scope().indexOf("结构位置指标")));
        assertEquals("点赞数", likes.label());
        assertEquals("interaction", likes.type());
        assertEquals("valid_zero 表示明确采集到的 0", likes.zeroValueRule());
        assertEquals("invalid 不进入该指标的有效样本", likes.invalidValueRule());
    }

    @Test
    void rejectsUnknownMetric() {
        assertThrows(IllegalArgumentException.class,
                () -> SocialNetworkMetricCatalog.require("unknown_metric"));
    }
}
