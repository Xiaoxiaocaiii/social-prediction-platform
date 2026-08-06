package com.prediction.api;

import com.prediction.common.ApiResponse;
import com.prediction.data.Video;
import com.prediction.data.VideoRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompareController {

    private final VideoRepository videoRepository;

    public CompareController(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    /** Overlay curves of 2~3 videos with per-video metrics. */
    @PostMapping("/api/compare")
    public ResponseEntity<ApiResponse<ComparePayload>> compare(@RequestBody CompareRequest request) {
        if (request.videoIds() == null || request.videoIds().size() < 2 || request.videoIds().size() > 3) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "需要 2~3 个 videoId"));
        }
        List<CompareItem> items = new ArrayList<>();
        for (String videoId : request.videoIds()) {
            Video video = videoRepository.findById(videoId).orElse(null);
            if (video == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "video not found: " + videoId));
            }
            items.add(new CompareItem(
                    video.videoId(),
                    video.title(),
                    video.userId(),
                    video.curve(),
                    video.day30(),
                    video.peakDay(),
                    video.decayRate()));
        }
        return ResponseEntity.ok(ApiResponse.ok(new ComparePayload(items)));
    }
}
