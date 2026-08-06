package com.prediction.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Path dbPath,
        Path coversDir,
        Path uploadsDir,
        Path coversZip,
        String predictionMode,
        String remotePredictionUrl) {
}
