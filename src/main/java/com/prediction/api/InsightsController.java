package com.prediction.api;

import com.prediction.common.ApiResponse;
import com.prediction.data.DecayCurves;
import com.prediction.data.InsightsPayload;
import com.prediction.data.VideoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InsightsController {

    private final VideoRepository videoRepository;

    public InsightsController(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<InsightsPayload>> insights() {
        InsightsPayload payload = new InsightsPayload(
                videoRepository.categoryStats(),
                videoRepository.correlationAndScatter(),
                videoRepository.decayCurves());
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }
}
