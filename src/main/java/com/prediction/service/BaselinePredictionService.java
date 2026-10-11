package com.prediction.service;

import com.prediction.data.ReferenceHit;
import com.prediction.data.Video;
import com.prediction.data.VideoRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        Map<String, Video> videosById = videoRepository.findByIds(
                hits.stream().map(ReferenceHit::videoId).toList());
        List<ReferenceHit> references = new ArrayList<>();
        double[] sum = new double[30];
        int count = 0;
        for (ReferenceHit hit : hits) {
            Video video = videosById.get(hit.videoId());
            if (video == null) {
                continue;
            }
            double[] curve = video.curve();
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
                "当前趋势由匹配到的历史视频 30 天热度曲线逐日平均形成；无命中时使用全站平均热度参考曲线。",
                prediction,
                references,
                hits.size(),
                count,
                "FTS5 检索并按 BM25 排序，短文本或异常查询使用 LIKE 兜底",
                "历史案例曲线按天平均计算参考趋势",
                count == 0,
                false);
    }
}
