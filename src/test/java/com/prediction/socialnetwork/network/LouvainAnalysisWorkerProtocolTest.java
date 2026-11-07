package com.prediction.socialnetwork.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LouvainAnalysisWorkerProtocolTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void workerMainRoundTripsCompleteMembershipAndIndependentlyCheckableQuality() throws Exception {
        List<NetworkNode> nodes = nodes();
        List<NetworkEdge> edges = List.of(
                edge("A", "B"),
                edge("B", "A"),
                edge("A", "C"),
                edge("B", "C"),
                edge("C", "C"));
        Path requestPath = temporaryDirectory.resolve("request.json");
        Path resultPath = temporaryDirectory.resolve("result.json");
        LouvainAnalysisWorkerProtocol.writeRequest(
                requestPath, LouvainAnalysisWorkerProtocol.requestFrom(nodes, edges));

        LouvainAnalysisWorkerMain.main(new String[] {
                requestPath.toString(),
                resultPath.toString()
        });
        LouvainAnalysisWorkerProtocol.WorkerResult result =
                LouvainAnalysisWorkerProtocol.readResult(resultPath, nodes, edges);

        assertEquals(LouvainAnalysisWorkerProtocol.WorkerStatus.COMPLETED, result.status());
        assertEquals(LouvainCommunityDetector.ALGORITHM_VERSION, result.algorithmVersion());
        assertEquals(LouvainAnalysisParameters.DEFAULT, result.parameters());
        assertEquals(1.0, result.resolution());
        assertEquals(CommunityDetectionStatus.COMPLETED, result.detection().status());
        assertTrue(result.detection().converged());
        assertEquals(4, result.quality().inputNodeCount());
        assertEquals(5, result.quality().inputDirectedRelationshipCount());
        assertEquals(3, result.quality().projectedUndirectedTieCount());
        assertEquals(1, result.quality().unassignedNodeCount());
        assertEquals(List.of("D"), result.quality().unassignedNodeIds());
        assertEquals(0.0, result.quality().modularity());
        assertTrue(Files.size(resultPath) <= LouvainAnalysisWorkerProtocol.MAX_RESULT_BYTES);
    }

    @Test
    void workerMainHonorsSensitivityParametersAndMutualProjection() throws Exception {
        List<NetworkNode> nodes = nodes();
        List<NetworkEdge> edges = List.of(
                edge("A", "B"),
                edge("B", "A"),
                edge("A", "C"));
        LouvainAnalysisParameters parameters = new LouvainAnalysisParameters(
                0.5,
                LouvainAnalysisParameters.NodeOrder.DEGREE_ASCENDING_THEN_ID_ASCENDING,
                LouvainAnalysisParameters.RelationProjection.MUTUAL_ONLY);
        Path requestPath = temporaryDirectory.resolve("sensitivity-request.json");
        Path resultPath = temporaryDirectory.resolve("sensitivity-result.json");
        LouvainAnalysisWorkerProtocol.writeRequest(
                requestPath,
                LouvainAnalysisWorkerProtocol.requestFrom(nodes, edges, parameters));

        LouvainAnalysisWorkerMain.main(new String[] {
                requestPath.toString(),
                resultPath.toString()
        });
        LouvainAnalysisWorkerProtocol.WorkerResult result =
                LouvainAnalysisWorkerProtocol.readResult(
                        resultPath, nodes, edges, parameters);

        assertEquals(LouvainAnalysisWorkerProtocol.WorkerStatus.COMPLETED, result.status());
        assertEquals(parameters, result.parameters());
        assertEquals(0.5, result.resolution());
        assertEquals("undirected_mutual", result.detection().relationProjection());
        assertEquals(3, result.quality().inputDirectedRelationshipCount());
        assertEquals(1, result.quality().projectedUndirectedTieCount());
        assertEquals(List.of("C", "D"), result.quality().unassignedNodeIds());
    }

    @Test
    void rejectsWorkerQualityThatDoesNotMatchItsPartition() throws Exception {
        List<NetworkNode> nodes = nodes();
        List<NetworkEdge> edges = List.of(edge("A", "B"), edge("B", "C"), edge("C", "A"));
        LouvainAnalysisWorkerProtocol.Request request =
                LouvainAnalysisWorkerProtocol.requestFrom(nodes, edges);
        Path requestPath = temporaryDirectory.resolve("tampered-request.json");
        Path resultPath = temporaryDirectory.resolve("tampered-result.json");
        LouvainAnalysisWorkerProtocol.writeRequest(requestPath, request);
        LouvainAnalysisWorkerMain.main(new String[] {
                requestPath.toString(),
                resultPath.toString()
        });
        LouvainAnalysisWorkerProtocol.WorkerResult valid =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(resultPath.toFile(),
                                LouvainAnalysisWorkerProtocol.WorkerResult.class);
        LouvainQualitySummary quality = valid.quality();
        LouvainAnalysisWorkerProtocol.WorkerResult tampered =
                new LouvainAnalysisWorkerProtocol.WorkerResult(
                        valid.schemaVersion(),
                        valid.status(),
                        valid.algorithmVersion(),
                        valid.resolution(),
                        valid.parameters(),
                        valid.detection(),
                        new LouvainQualitySummary(
                                quality.inputNodeCount(),
                                quality.inputDirectedRelationshipCount(),
                                quality.assignedNodeCount(),
                                quality.unassignedNodeIds(),
                                quality.projectedUndirectedTieCount(),
                                quality.internalTieCount(),
                                quality.crossCommunityTieCount(),
                                quality.internalTieCountByCommunity(),
                                quality.modularity() + 0.25),
                        valid.terminationDetail());
        LouvainAnalysisWorkerProtocol.writeResult(resultPath, tampered);

        assertThrows(
                IOException.class,
                () -> LouvainAnalysisWorkerProtocol.readResult(resultPath, nodes, edges));
    }

    @Test
    void rejectsResultProducedWithDifferentSensitivityParameters() throws Exception {
        List<NetworkNode> nodes = nodes();
        List<NetworkEdge> edges = List.of(edge("A", "B"), edge("B", "C"));
        Path requestPath = temporaryDirectory.resolve("parameter-request.json");
        Path resultPath = temporaryDirectory.resolve("parameter-result.json");
        LouvainAnalysisParameters expected = new LouvainAnalysisParameters(
                0.5,
                LouvainAnalysisParameters.NodeOrder.DEGREE_ASCENDING_THEN_ID_ASCENDING,
                LouvainAnalysisParameters.RelationProjection.ANY_DIRECTION);
        LouvainAnalysisWorkerProtocol.writeRequest(
                requestPath,
                LouvainAnalysisWorkerProtocol.requestFrom(nodes, edges));
        LouvainAnalysisWorkerMain.main(new String[] {
                requestPath.toString(),
                resultPath.toString()
        });

        assertThrows(
                IOException.class,
                () -> LouvainAnalysisWorkerProtocol.readResult(
                        resultPath, nodes, edges, expected));
    }

    @Test
    void rejectsRequestRelationshipsOutsideTheDeclaredNodeSet() throws Exception {
        Path requestPath = temporaryDirectory.resolve("invalid-request.json");
        LouvainAnalysisWorkerProtocol.writeRequest(
                requestPath,
                new LouvainAnalysisWorkerProtocol.Request(
                        LouvainAnalysisWorkerProtocol.SCHEMA_VERSION,
                        List.of(new LouvainAnalysisWorkerProtocol.InputNode(
                                "A", NetworkNodeType.PROFILE)),
                        List.of(new LouvainAnalysisWorkerProtocol.Relationship("A", "missing")),
                        LouvainAnalysisParameters.DEFAULT));

        assertThrows(
                IOException.class,
                () -> LouvainAnalysisWorkerProtocol.readRequest(requestPath));
    }

    private static List<NetworkNode> nodes() {
        return List.of(
                node("A", NetworkNodeType.PROFILE),
                node("B", NetworkNodeType.EXTERNAL),
                node("C", NetworkNodeType.PROFILE),
                node("D", NetworkNodeType.PROFILE));
    }

    private static NetworkNode node(String id, NetworkNodeType type) {
        return new NetworkNode(id, type, 0, 0, 0.0, false);
    }

    private static NetworkEdge edge(String source, String target) {
        return new NetworkEdge(source, target, "following");
    }
}
