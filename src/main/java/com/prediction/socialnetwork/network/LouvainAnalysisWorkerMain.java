package com.prediction.socialnetwork.network;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class LouvainAnalysisWorkerMain {

    private LouvainAnalysisWorkerMain() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected input and output paths");
        }

        Path inputPath = Path.of(arguments[0]);
        Path outputPath = Path.of(arguments[1]);
        LouvainAnalysisWorkerProtocol.WorkerResult result;
        try {
            LouvainAnalysisWorkerProtocol.Request request =
                    LouvainAnalysisWorkerProtocol.readRequest(inputPath);
            List<NetworkNode> nodes = request.nodes().stream()
                    .map(node -> new NetworkNode(
                            node.id(), node.nodeType(), 0, 0, 0.0, false))
                    .toList();
            List<NetworkEdge> edges = request.relationships().stream()
                    .map(relationship -> new NetworkEdge(
                            relationship.source(), relationship.target(), "following"))
                    .toList();

            CommunityDetectionResult detection =
                    new LouvainCommunityDetector().detect(
                            nodes, edges, request.parameters());
            LouvainQualitySummary quality =
                    new LouvainQualityCalculator().calculate(
                            nodes,
                            edges,
                            detection.communityByNodeId(),
                            request.parameters());
            result = new LouvainAnalysisWorkerProtocol.WorkerResult(
                    LouvainAnalysisWorkerProtocol.SCHEMA_VERSION,
                    LouvainAnalysisWorkerProtocol.WorkerStatus.COMPLETED,
                    LouvainCommunityDetector.ALGORITHM_VERSION,
                    request.parameters().resolution(),
                    request.parameters(),
                    detection,
                    quality,
                    null);
        } catch (IOException | RuntimeException exception) {
            result = new LouvainAnalysisWorkerProtocol.WorkerResult(
                    LouvainAnalysisWorkerProtocol.SCHEMA_VERSION,
                    LouvainAnalysisWorkerProtocol.WorkerStatus.FAILED,
                    null,
                    0.0,
                    null,
                    null,
                    null,
                    "worker_failure_" + exception.getClass().getSimpleName());
        }
        LouvainAnalysisWorkerProtocol.writeResult(outputPath, result);
    }
}
