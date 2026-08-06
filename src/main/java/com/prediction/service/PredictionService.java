package com.prediction.service;

public interface PredictionService {

    PredictionResult predict(String text, String imagePath, int topK);
}
