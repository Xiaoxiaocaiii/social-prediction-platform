package com.prediction.data;

import java.util.Map;

/** Average popularity curves: overall and per category. */
public record DecayCurves(double[] overall, Map<String, double[]> byCategory) {
}
