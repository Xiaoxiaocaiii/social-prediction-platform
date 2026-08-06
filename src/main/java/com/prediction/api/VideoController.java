package com.prediction.api;

import com.prediction.common.ApiResponse;
import com.prediction.data.PageResult;
import com.prediction.data.Video;
import com.prediction.data.VideoRepository;
import com.prediction.data.VideoSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoRepository videoRepository;

    public VideoController(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    /** Paged browse: optional category filter, sort and search term. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<VideoSummary>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q) {
        PageResult<VideoSummary> result = videoRepository.search(category, sort, q, page, pageSize);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<Video>> detail(@PathVariable String videoId) {
        return videoRepository.findById(videoId)
                .map(video -> ResponseEntity.ok(ApiResponse.ok(video)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "video not found: " + videoId)));
    }
}
