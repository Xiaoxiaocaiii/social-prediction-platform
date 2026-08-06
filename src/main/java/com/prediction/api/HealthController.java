package com.prediction.api;

import com.prediction.data.VideoRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final VideoRepository videoRepository;

    public HealthController(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "totalVideos", videoRepository.count(),
                "categories", videoRepository.countCategories());
    }
}
