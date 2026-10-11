package com.prediction.api;

import com.prediction.common.ApiResponse;
import com.prediction.config.AppProperties;
import com.prediction.service.PredictionResult;
import com.prediction.service.PredictionServiceSelector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class PredictController {

    private final PredictionServiceSelector predictionServiceSelector;
    private final AppProperties props;

    public PredictController(PredictionServiceSelector predictionServiceSelector, AppProperties props) {
        this.predictionServiceSelector = predictionServiceSelector;
        this.props = props;
    }

    @PostMapping(value = "/predict", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PredictionResult>> predict(
            @RequestParam("text") String text,
            @RequestParam(defaultValue = "30") int topK,
            @RequestPart(value = "file", required = false) MultipartFile file) throws IOException {
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "text 不能为空"));
        }
        String imagePath = null;
        if (file != null && !file.isEmpty()) {
            if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "只支持图片文件"));
            }
            imagePath = saveUpload(file);
        }
        PredictionResult result = predictionServiceSelector.current().predict(text.trim(), imagePath, topK);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    private String saveUpload(MultipartFile file) throws IOException {
        Path dir = props.uploadsDir();
        Files.createDirectories(dir);
        String filename = UUID.randomUUID().toString().replace("-", "") + extensionOf(file.getOriginalFilename());
        Path dest = dir.resolve(filename);
        file.transferTo(dest);
        return dest.toString();
    }

    private static String extensionOf(String originalName) {
        if (originalName == null) {
            return ".img";
        }
        int dot = originalName.lastIndexOf('.');
        return dot >= 0 && dot < originalName.length() - 1
                ? originalName.substring(dot).toLowerCase()
                : ".img";
    }
}
