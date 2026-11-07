package com.prediction.socialnetwork.network;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class CliqueEnumerationWorkerMain {

    private CliqueEnumerationWorkerMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 5) {
            throw new IllegalArgumentException("Expected input, output, and three bounded limits");
        }

        Path inputPath = Path.of(arguments[0]);
        Path outputPath = Path.of(arguments[1]);
        long requestedTimeLimitNanos = positiveLong(arguments[2], "time limit");
        int requestedMaxCliques = Math.toIntExact(positiveLong(arguments[3], "clique limit"));
        long requestedMaxArrayBytes = positiveLong(arguments[4], "result array limit");

        CliqueEnumerationLimits limits = new CliqueEnumerationLimits(
                Duration.ofNanos(Math.min(
                        requestedTimeLimitNanos,
                        CliqueEnumerationLimits.DEFAULT.timeLimit().toNanos())),
                Math.min(requestedMaxCliques, CliqueEnumerationLimits.DEFAULT.maxCliques()),
                Math.min(
                        requestedMaxArrayBytes,
                        CliqueEnumerationWorkerProtocol.MAX_CLIQUE_ARRAY_BYTES));
        CliqueEnumerationWorkerProtocol.Request request =
                CliqueEnumerationWorkerProtocol.readRequest(inputPath);
        List<NetworkNode> nodes = request.nodeIds().stream()
                .map(id -> new NetworkNode(id, NetworkNodeType.PROFILE, 0, 0, 0.0, false))
                .toList();
        List<NetworkEdge> edges = request.relationships().stream()
                .map(relationship -> new NetworkEdge(
                        relationship.source(), relationship.target(), "following"))
                .toList();

        CliqueEnumerationResult result =
                new MaximalCliqueEnumerator().enumerate(nodes, edges, limits);
        CliqueEnumerationWorkerProtocol.writeResult(outputPath, result);
    }

    private static long positiveLong(String value, String label) {
        long parsed = Long.parseLong(value);
        if (parsed < 1) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return parsed;
    }
}
