package com.prediction.service;

import com.prediction.data.ReferenceHit;
import java.util.List;

public record PredictionResult(
        String mode,
        String note,
        double[] prediction,
        List<ReferenceHit> references) {
}
