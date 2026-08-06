package com.prediction.api;

import com.prediction.data.CoverService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CoverController {

    private final CoverService coverService;

    public CoverController(CoverService coverService) {
        this.coverService = coverService;
    }

    /**
     * Serve a cover at /covers/{prefix}/{videoId}.jpg.
     * Extracts from the local zip on demand, falls back to a placeholder.
     */
    @GetMapping("/covers/{prefix}/{filename}")
    public ResponseEntity<Resource> cover(
            @PathVariable String prefix,
            @PathVariable String filename) {
        return coverService.resolve(prefix, filename)
                .map(resource -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource))
                .orElseGet(() -> ResponseEntity.ok()
                        .contentType(MediaType.valueOf("image/svg+xml"))
                        .body(coverService.placeholder()));
    }
}
