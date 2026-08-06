package com.prediction.service;

import com.prediction.config.AppProperties;
import org.springframework.stereotype.Service;

@Service
public class PredictionServiceSelector {

    private final BaselinePredictionService baseline;
    private final RemotePredictionService remote;
    private final AppProperties props;

    public PredictionServiceSelector(
            BaselinePredictionService baseline,
            RemotePredictionService remote,
            AppProperties props) {
        this.baseline = baseline;
        this.remote = remote;
        this.props = props;
    }

    public PredictionService current() {
        return "remote".equalsIgnoreCase(props.predictionMode()) ? remote : baseline;
    }
}
