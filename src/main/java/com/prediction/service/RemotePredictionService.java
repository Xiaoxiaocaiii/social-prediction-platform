package com.prediction.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.config.AppProperties;
import com.prediction.data.ReferenceHit;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Calls the Python model service over HTTP. Dormant until the model team
 * delivers; activated by switching app.prediction-mode to "remote".
 */
@Service
public class RemotePredictionService implements PredictionService {

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public RemotePredictionService(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public PredictionResult predict(String text, String imagePath, int topK) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", text);
            body.put("imagePath", imagePath);
            body.put("topK", topK);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.remotePredictionUrl()))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("remote prediction failed: HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            double[] prediction = toDoubleArray(root.path("prediction"));
            List<ReferenceHit> references = new ArrayList<>();
            for (JsonNode node : root.path("references")) {
                references.add(new ReferenceHit(
                        node.path("videoId").asText(""),
                        node.path("title").asText(""),
                        node.path("coverPath").asText(""),
                        node.path("userId").asText(""),
                        node.path("day30").asDouble(0),
                        node.path("score").asDouble(0)));
            }
            return new PredictionResult("remote", "远程模型服务预测", prediction, references);
        } catch (Exception e) {
            throw new IllegalStateException("remote prediction unavailable: " + e.getMessage(), e);
        }
    }

    private static double[] toDoubleArray(JsonNode array) {
        double[] out = new double[array.size()];
        for (int i = 0; i < array.size(); i++) {
            out[i] = array.get(i).asDouble(0);
        }
        return out;
    }
}
