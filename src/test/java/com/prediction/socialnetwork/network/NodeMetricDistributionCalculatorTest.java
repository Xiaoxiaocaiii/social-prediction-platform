package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NodeMetricDistributionCalculatorTest {

    private final NodeMetricDistributionCalculator calculator =
            new NodeMetricDistributionCalculator();

    @Test
    void summarizesZerosAndUsesNearestRankPercentiles() {
        List<Double> values = IntStream.range(0, 20)
                .asDoubleStream()
                .boxed()
                .toList();

        NodeMetricDistributionCalculator.Summary summary = calculator.summarize(values);

        assertEquals(20, summary.sampleSize());
        assertEquals(1, summary.zeroCount());
        assertEquals(0.05, summary.zeroRatio(), 1e-12);
        assertEquals(0.0, summary.min(), 1e-12);
        assertEquals(9.5, summary.mean(), 1e-12);
        assertEquals(9.5, summary.median(), 1e-12);
        assertEquals(17.0, summary.p90(), 1e-12);
        assertEquals(18.0, summary.p95(), 1e-12);
        assertEquals(19.0, summary.max(), 1e-12);
    }

    @Test
    void computesOddSampleMedianAndAllZeroRatio() {
        NodeMetricDistributionCalculator.Summary summary =
                calculator.summarize(List.of(0.0, 0.0, 0.0));

        assertEquals(3, summary.sampleSize());
        assertEquals(3, summary.zeroCount());
        assertEquals(1.0, summary.zeroRatio(), 1e-12);
        assertEquals(0.0, summary.median(), 1e-12);
        assertEquals(0.0, summary.p90(), 1e-12);
        assertEquals(0.0, summary.p95(), 1e-12);
    }

    @Test
    void returnsNullStatisticsForAnEmptySample() {
        NodeMetricDistributionCalculator.Summary summary =
                calculator.summarize(List.of());

        assertEquals(0, summary.sampleSize());
        assertEquals(0, summary.zeroCount());
        assertNull(summary.zeroRatio());
        assertNull(summary.min());
        assertNull(summary.mean());
        assertNull(summary.median());
        assertNull(summary.p90());
        assertNull(summary.p95());
        assertNull(summary.max());
    }

    @Test
    void rejectsMissingAndNonFiniteMetricValues() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.summarize(List.of(Double.NaN)));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.summarize(Arrays.asList(0.0, null)));
    }
}
