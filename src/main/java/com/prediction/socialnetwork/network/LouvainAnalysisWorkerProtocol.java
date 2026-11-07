package com.prediction.socialnetwork.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LouvainAnalysisWorkerProtocol {

    public static final int SCHEMA_VERSION = 2;
    public static final long MAX_REQUEST_BYTES = 16 * CliqueEnumerationLimits.MEBIBYTE;
    public static final long MAX_RESULT_BYTES = 2 * CliqueEnumerationLimits.MEBIBYTE;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private LouvainAnalysisWorkerProtocol() {
    }

    public static Request requestFrom(
            List<NetworkNode> nodes, List<NetworkEdge> directedEdges) {
        return requestFrom(nodes, directedEdges, LouvainAnalysisParameters.DEFAULT);
    }

    public static Request requestFrom(
            List<NetworkNode> nodes,
            List<NetworkEdge> directedEdges,
            LouvainAnalysisParameters parameters) {
        return new Request(
                SCHEMA_VERSION,
                nodes.stream()
                        .map(node -> new InputNode(node.id(), node.nodeType()))
                        .toList(),
                directedEdges.stream()
                        .map(edge -> new Relationship(edge.source(), edge.target()))
                        .toList(),
                parameters);
    }

    public static void writeRequest(Path path, Request request) throws IOException {
        byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(request);
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new PayloadTooLargeException("Louvain worker input exceeds 16 MiB");
        }
        writeAtomically(path, bytes);
    }

    public static Request readRequest(Path path) throws IOException {
        if (Files.size(path) > MAX_REQUEST_BYTES) {
            throw new PayloadTooLargeException("Louvain worker input exceeds 16 MiB");
        }
        Request request = OBJECT_MAPPER.readValue(path.toFile(), Request.class);
        validateRequest(request);
        return request;
    }

    public static void writeResult(Path path, WorkerResult result) throws IOException {
        byte[] bytes;
        try {
            bytes = encode(result);
        } catch (PayloadTooLargeException oversized) {
            bytes = encode(new WorkerResult(
                    SCHEMA_VERSION,
                    WorkerStatus.FAILED,
                    null,
                    0.0,
                    null,
                    null,
                    null,
                    "result_size_limit"));
        }
        writeAtomically(path, bytes);
    }

    public static WorkerResult readResult(
            Path path,
            List<NetworkNode> expectedNodes,
            List<NetworkEdge> expectedEdges) throws IOException {
        return readResult(
                path,
                expectedNodes,
                expectedEdges,
                LouvainAnalysisParameters.DEFAULT);
    }

    public static WorkerResult readResult(
            Path path,
            List<NetworkNode> expectedNodes,
            List<NetworkEdge> expectedEdges,
            LouvainAnalysisParameters expectedParameters) throws IOException {
        if (Files.size(path) > MAX_RESULT_BYTES) {
            throw new PayloadTooLargeException("Louvain worker result exceeds 2 MiB");
        }
        WorkerResult result = OBJECT_MAPPER.readValue(path.toFile(), WorkerResult.class);
        validateResult(result, expectedNodes, expectedEdges, expectedParameters);
        return result;
    }

    private static byte[] encode(WorkerResult result) throws IOException {
        byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(result);
        if (bytes.length > MAX_RESULT_BYTES) {
            throw new PayloadTooLargeException("Louvain worker result exceeds 2 MiB");
        }
        return bytes;
    }

    private static void validateRequest(Request request) throws IOException {
        if (request == null
                || request.schemaVersion() != SCHEMA_VERSION
                || request.nodes() == null
                || request.relationships() == null
                || request.parameters() == null) {
            throw new IOException("Louvain worker request envelope is invalid");
        }
        Set<String> nodeIds = new HashSet<>();
        for (InputNode node : request.nodes()) {
            if (node == null
                    || node.id() == null
                    || node.id().isBlank()
                    || node.nodeType() == null
                    || !nodeIds.add(node.id())) {
                throw new IOException("Louvain worker request has an invalid node");
            }
        }
        for (Relationship relationship : request.relationships()) {
            if (relationship == null
                    || relationship.source() == null
                    || relationship.source().isBlank()
                    || relationship.target() == null
                    || relationship.target().isBlank()
                    || !nodeIds.contains(relationship.source())
                    || !nodeIds.contains(relationship.target())) {
                throw new IOException("Louvain worker request has an invalid relationship");
            }
        }
    }

    private static void validateResult(
            WorkerResult result,
            List<NetworkNode> expectedNodes,
            List<NetworkEdge> expectedEdges,
            LouvainAnalysisParameters expectedParameters) throws IOException {
        if (result == null
                || result.schemaVersion() != SCHEMA_VERSION
                || result.status() == null) {
            throw new IOException("Louvain worker result envelope is invalid");
        }
        if (result.status() == WorkerStatus.FAILED) {
            if (result.detection() != null
                    || result.quality() != null
                    || result.algorithmVersion() != null
                    || result.resolution() != 0.0
                    || result.parameters() != null
                    || result.terminationDetail() == null
                    || result.terminationDetail().isBlank()) {
                throw new IOException("Louvain worker failure result is invalid");
            }
            return;
        }
        if (result.detection() == null
                || result.quality() == null
                || result.terminationDetail() != null
                || expectedParameters == null
                || !expectedParameters.equals(result.parameters())
                || !LouvainCommunityDetector.ALGORITHM_VERSION.equals(
                        result.algorithmVersion())
                || Double.compare(result.resolution(), expectedParameters.resolution()) != 0
                || result.detection().status() != CommunityDetectionStatus.COMPLETED
                || !LouvainCommunityDetector.ALGORITHM.equals(result.detection().algorithm())
                || !expectedParameters.relationProjection().resultName().equals(
                        result.detection().relationProjection())
                || result.detection().iterations() < 0
                || result.detection().iterations() > LouvainCommunityDetector.MAX_ITERATIONS) {
            throw new IOException("Louvain worker completed result is invalid");
        }

        Set<String> nodeIds = new HashSet<>();
        Map<String, NetworkNodeType> nodeTypes = new HashMap<>();
        for (NetworkNode node : expectedNodes) {
            if (!nodeIds.add(node.id())) {
                throw new IOException("Expected Louvain input has duplicate node ids");
            }
            nodeTypes.put(node.id(), node.nodeType());
        }
        Map<String, String> assignments = result.detection().communityByNodeId();
        if (assignments.entrySet().stream().anyMatch(entry ->
                !nodeIds.contains(entry.getKey())
                        || entry.getValue() == null
                        || entry.getValue().isBlank())) {
            throw new IOException("Louvain result assigns a node outside the input");
        }

        Map<String, Integer> memberCounts = new HashMap<>();
        Map<String, Integer> profileCounts = new HashMap<>();
        Map<String, Integer> externalCounts = new HashMap<>();
        assignments.forEach((nodeId, communityId) -> {
            if (communityId != null && !communityId.isBlank()) {
                memberCounts.merge(communityId, 1, Integer::sum);
                if (nodeTypes.get(nodeId) == NetworkNodeType.PROFILE) {
                    profileCounts.merge(communityId, 1, Integer::sum);
                } else {
                    externalCounts.merge(communityId, 1, Integer::sum);
                }
            }
        });
        Map<String, NetworkCommunity> communities = new HashMap<>();
        for (NetworkCommunity community : result.detection().communities()) {
            if (community == null
                    || community.id() == null
                    || community.id().isBlank()
                    || communities.putIfAbsent(community.id(), community) != null
                    || community.nodeCount() != memberCounts.getOrDefault(community.id(), 0)
                    || community.profileNodeCount()
                            != profileCounts.getOrDefault(community.id(), 0)
                    || community.externalNodeCount()
                            != externalCounts.getOrDefault(community.id(), 0)) {
                throw new IOException("Louvain community summaries do not match membership");
            }
        }
        if (!communities.keySet().equals(memberCounts.keySet())) {
            throw new IOException("Louvain community summary coverage is incomplete");
        }

        LouvainQualitySummary expectedQuality;
        try {
            expectedQuality = new LouvainQualityCalculator().calculate(
                    expectedNodes, expectedEdges, assignments, expectedParameters);
        } catch (RuntimeException exception) {
            throw new IOException("Louvain membership quality validation failed", exception);
        }
        if (!sameQuality(expectedQuality, result.quality())
                || result.detection().unassignedNodeCount()
                        != expectedQuality.unassignedNodeCount()
                || communities.values().stream().anyMatch(community ->
                        community.internalTieCount()
                                != expectedQuality.internalTieCountByCommunity()
                                        .getOrDefault(community.id(), -1))) {
            throw new IOException("Louvain quality summary does not match worker membership");
        }
    }

    private static boolean sameQuality(
            LouvainQualitySummary expected, LouvainQualitySummary actual) {
        if (expected.inputNodeCount() != actual.inputNodeCount()
                || expected.inputDirectedRelationshipCount()
                        != actual.inputDirectedRelationshipCount()
                || expected.assignedNodeCount() != actual.assignedNodeCount()
                || !expected.unassignedNodeIds().equals(actual.unassignedNodeIds())
                || expected.projectedUndirectedTieCount()
                        != actual.projectedUndirectedTieCount()
                || expected.internalTieCount() != actual.internalTieCount()
                || expected.crossCommunityTieCount() != actual.crossCommunityTieCount()
                || !expected.internalTieCountByCommunity()
                        .equals(actual.internalTieCountByCommunity())) {
            return false;
        }
        if (expected.modularity() == null || actual.modularity() == null) {
            return expected.modularity() == actual.modularity();
        }
        return Double.isFinite(actual.modularity())
                && Math.abs(expected.modularity() - actual.modularity()) <= 1e-12;
    }

    private static void writeAtomically(Path path, byte[] bytes) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".pending");
        try {
            Files.write(
                    temporary,
                    bytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public record Request(
            int schemaVersion,
            List<InputNode> nodes,
            List<Relationship> relationships,
            LouvainAnalysisParameters parameters) {
    }

    public record InputNode(String id, NetworkNodeType nodeType) {
    }

    public record Relationship(String source, String target) {
    }

    public record WorkerResult(
            int schemaVersion,
            WorkerStatus status,
            String algorithmVersion,
            double resolution,
            LouvainAnalysisParameters parameters,
            CommunityDetectionResult detection,
            LouvainQualitySummary quality,
            String terminationDetail) {
    }

    public enum WorkerStatus {
        COMPLETED,
        FAILED
    }

    public static final class PayloadTooLargeException extends IOException {
        public PayloadTooLargeException(String message) {
            super(message);
        }
    }
}
