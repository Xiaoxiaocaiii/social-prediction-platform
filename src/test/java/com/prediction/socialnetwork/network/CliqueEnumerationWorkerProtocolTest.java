package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliqueEnumerationWorkerProtocolTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void workerArtifactUsesExpandedBoundWithoutChangingSummaryReserve() {
        assertEquals(16L * CliqueEnumerationLimits.MEBIBYTE,
                CliqueEnumerationWorkerProtocol.MAX_RESULT_ARTIFACT_BYTES);
        assertEquals(
                CliqueEnumerationWorkerProtocol.MAX_RESULT_ARTIFACT_BYTES
                        - 256L * CliqueEnumerationLimits.KIBIBYTE,
                CliqueEnumerationWorkerProtocol.MAX_CLIQUE_ARRAY_BYTES);
    }

    @Test
    void roundTripsSyntheticInputAndResultThroughTheWorkerProtocol() throws Exception {
        List<NetworkNode> nodes = List.of(
                node("A"), node("B"), node("C"), node("D"));
        List<NetworkEdge> edges = List.of(
                edge("A", "B"), edge("A", "C"), edge("B", "C"));
        Path requestPath = temporaryDirectory.resolve("request.json");
        Path resultPath = temporaryDirectory.resolve("result.json");
        CliqueEnumerationWorkerProtocol.Request request =
                CliqueEnumerationWorkerProtocol.requestFrom(nodes, edges);

        CliqueEnumerationWorkerProtocol.writeRequest(requestPath, request);
        CliqueEnumerationWorkerProtocol.Request decodedRequest =
                CliqueEnumerationWorkerProtocol.readRequest(requestPath);
        CliqueEnumerationLimits limits = workerLimits();
        CliqueEnumerationResult result = new MaximalCliqueEnumerator()
                .enumerate(nodes, edges, limits);
        CliqueEnumerationWorkerProtocol.writeResult(resultPath, result);
        CliqueEnumerationResult decodedResult =
                CliqueEnumerationWorkerProtocol.readResult(resultPath, limits);

        assertEquals(request, decodedRequest);
        assertTrue(Files.size(resultPath)
                <= CliqueEnumerationWorkerProtocol.MAX_RESULT_ARTIFACT_BYTES);
        assertEquals(result, decodedResult);
        assertEquals(0, decodedResult.observedParticipationCounts().get("D"));
    }

    @Test
    void roundTripsCompleteSummaryWithBoundedCliqueMemberships() throws Exception {
        List<List<String>> retained = List.of(List.of("A", "B", "C"));
        CliqueEnumerationLimits limits = workerLimits();
        CliqueEnumerationResult result = new CliqueEnumerationResult(
                retained,
                CliqueEnumerationStatus.COMPLETED,
                limits,
                new ObjectMapper().writeValueAsBytes(retained).length,
                null,
                2,
                java.util.Map.of(3, 2),
                java.util.Map.of("A", 1, "B", 1, "C", 1, "D", 1, "E", 1, "F", 1),
                false);
        Path resultPath = temporaryDirectory.resolve("summary-result.json");

        CliqueEnumerationWorkerProtocol.writeResult(resultPath, result);
        CliqueEnumerationResult decoded =
                CliqueEnumerationWorkerProtocol.readResult(resultPath, limits);

        assertTrue(Files.size(resultPath)
                <= CliqueEnumerationWorkerProtocol.MAX_RESULT_ARTIFACT_BYTES);
        assertEquals(result, decoded);
        assertTrue(decoded.isComplete());
        assertFalse(decoded.membershipListComplete());
        assertEquals(2, decoded.enumeratedCliqueCount());
        assertEquals(java.util.Map.of(3, 2), decoded.cliqueSizeDistribution());
        assertEquals(6, decoded.observedParticipationCounts().size());
    }

    @Test
    void workerMainWritesSchemaV2SummaryForSyntheticInput() throws Exception {
        List<NetworkNode> nodes = List.of(
                node("A"), node("B"), node("C"), node("D"));
        List<NetworkEdge> edges = List.of(
                edge("A", "B"), edge("A", "C"), edge("B", "C"));
        Path requestPath = temporaryDirectory.resolve("worker-request.json");
        Path resultPath = temporaryDirectory.resolve("worker-result.json");
        CliqueEnumerationWorkerProtocol.writeRequest(
                requestPath,
                CliqueEnumerationWorkerProtocol.requestFrom(nodes, edges));

        CliqueEnumerationWorkerMain.main(new String[] {
                requestPath.toString(),
                resultPath.toString(),
                Long.toString(CliqueEnumerationLimits.DEFAULT.timeLimit().toNanos()),
                Integer.toString(CliqueEnumerationLimits.DEFAULT.maxCliques()),
                Long.toString(CliqueEnumerationWorkerProtocol.MAX_CLIQUE_ARRAY_BYTES)
        });
        CliqueEnumerationResult result = CliqueEnumerationWorkerProtocol.readResult(
                resultPath, workerLimits());

        assertEquals(CliqueEnumerationStatus.COMPLETED, result.status());
        assertEquals(1, result.enumeratedCliqueCount());
        assertEquals(java.util.Map.of(3, 1), result.cliqueSizeDistribution());
        assertEquals(0, result.nodeParticipationCounts().get("D"));
        assertTrue(result.membershipListComplete());
        assertTrue(Files.size(resultPath)
                <= CliqueEnumerationWorkerProtocol.MAX_RESULT_ARTIFACT_BYTES);
    }

    @Test
    void rejectsTheWholeArtifactWhenMetadataWouldPushItPastTwoMebibytes() throws Exception {
        CliqueEnumerationLimits limits = workerLimits();
        CliqueEnumerationResult oversizedEnvelope = new CliqueEnumerationResult(
                List.of(),
                CliqueEnumerationStatus.FAILED,
                limits,
                2,
                "x".repeat((int) CliqueEnumerationWorkerProtocol.MAX_RESULT_ARTIFACT_BYTES));
        Path resultPath = temporaryDirectory.resolve("oversized-result.json");

        CliqueEnumerationWorkerProtocol.writeResult(resultPath, oversizedEnvelope);
        CliqueEnumerationResult decoded =
                CliqueEnumerationWorkerProtocol.readResult(resultPath, limits);

        assertTrue(Files.size(resultPath)
                <= CliqueEnumerationWorkerProtocol.MAX_RESULT_ARTIFACT_BYTES);
        assertEquals(CliqueEnumerationStatus.RESULT_SIZE_LIMIT, decoded.status());
        assertFalse(decoded.isComplete());
        assertEquals(0, decoded.retainedCliqueCount());
    }

    @Test
    void rejectsAnOversizedFileBeforeParsingIt() throws Exception {
        Path resultPath = temporaryDirectory.resolve("too-large.json");
        Files.write(
                resultPath,
                new byte[(int) CliqueEnumerationWorkerProtocol.MAX_RESULT_ARTIFACT_BYTES + 1]);

        assertThrows(
                CliqueEnumerationWorkerProtocol.PayloadTooLargeException.class,
                () -> CliqueEnumerationWorkerProtocol.readResult(
                        resultPath, workerLimits()));
    }

    @Test
    void rejectsMalformedResultEnvelopes() throws Exception {
        Path resultPath = temporaryDirectory.resolve("malformed-result.json");
        Files.writeString(resultPath, "{\"schemaVersion\":1,\"status\":\"COMPLETED\"}");

        assertThrows(
                Exception.class,
                () -> CliqueEnumerationWorkerProtocol.readResult(
                        resultPath, workerLimits()));
    }

    private static CliqueEnumerationLimits workerLimits() {
        return new CliqueEnumerationLimits(
                CliqueEnumerationLimits.DEFAULT.timeLimit(),
                CliqueEnumerationLimits.DEFAULT.maxCliques(),
                CliqueEnumerationWorkerProtocol.MAX_CLIQUE_ARRAY_BYTES);
    }

    private static NetworkNode node(String id) {
        return new NetworkNode(id, NetworkNodeType.PROFILE, 0, 0, 0.0, false);
    }

    private static NetworkEdge edge(String source, String target) {
        return new NetworkEdge(source, target, "following");
    }
}
