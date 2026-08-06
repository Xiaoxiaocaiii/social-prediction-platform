package com.prediction.service;

import com.prediction.data.ReferenceHit;
import com.prediction.data.Video;
import com.prediction.data.VideoRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Real data baseline: average curves of the top-K text-similar videos. */
@Service
public class BaselinePredictionService implements PredictionService {

    private final VideoRepository videoRepository;

    public BaselinePredictionService(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Override
    public PredictionResult predict(String text, String imagePath, int topK) {
        List<ReferenceHit> hits = videoRepository.searchRelevant(text, topK);
        List<ReferenceHit> references = new ArrayList<>();
        double[] sum = new double[30];
        int count = 0;
        for (ReferenceHit hit : hits) {
            Optional<Video> video = videoRepository.findById(hit.videoId());
            if (video.isEmpty()) {
                continue;
            }
            double[] curve = video.get().curve();
            for (int d = 0; d < 30; d++) {
                sum[d] += curve[d];
            }
            count++;
            references.add(hit);
        }

        double[] prediction = new double[30];
        if (count > 0) {
            for (int d = 0; d < 30; d++) {
                prediction[d] = sum[d] / count;
            }
        } else {
            double[] overall = videoRepository.decayCurves().overall();
            System.arraycopy(overall, 0, prediction, 0, 30);
        }
        return new PredictionResult(
                "baseline",
                "基线预测：参考案例真实曲线平均，模型接入后替换",
                prediction,
                references);
    }
}
