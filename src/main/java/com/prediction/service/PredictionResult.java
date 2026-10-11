package com.prediction.service;

import com.prediction.data.ReferenceHit;
import java.util.List;

public record PredictionResult(
        String mode,
        String note,
        double[] prediction,
        List<ReferenceHit> references,
        Integer matchedCount,
        Integer usedSampleCount,
        String retrievalMethod,
        String aggregationMethod,
        Boolean fallback,
        Boolean imageUsed) {

    public PredictionResult(
            String mode,
            String note,
            double[] prediction,
            List<ReferenceHit> references) {
        this(mode, note, prediction, references, null, null, null, null, null, null);
    }
}
