package com.prediction.socialnetwork.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CliqueEnumerationWorkerProtocol {

    public static final int SCHEMA_VERSION = 2;
    public static final String ALGORITHM_VERSION =
            "maximal-clique-bron-kerbosch-pivot-v1";
    public static final long MAX_RESULT_ARTIFACT_BYTES = 16 * CliqueEnumerationLimits.MEBIBYTE;
    private static final long SUMMARY_RESERVE_BYTES = 256 * CliqueEnumerationLimits.KIBIBYTE;
    public static final long MAX_CLIQUE_ARRAY_BYTES =
            MAX_RESULT_ARTIFACT_BYTES - SUMMARY_RESERVE_BYTES;
    public static final long MAX_REQUEST_BYTES = 16 * CliqueEnumerationLimits.MEBIBYTE;
    public static final CliqueEnumerationLimits DEFAULT_WORKER_LIMITS =
            new CliqueEnumerationLimits(
                    CliqueEnumerationLimits.DEFAULT.timeLimit(),
                    CliqueEnumerationLimits.DEFAULT.maxCliques(),
                    MAX_CLIQUE_ARRAY_BYTES);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CliqueEnumerationWorkerProtocol() {
    }

    public static Request requestFrom(
            List<NetworkNode> nodes, List<NetworkEdge> directedEdges) {
        return new Request(
                nodes.stream().map(NetworkNode::id).toList(),
                directedEdges.stream()
                        .map(edge -> new Relationship(edge.source(), edge.target()))
                        .toList());
    }

    public static void writeRequest(Path path, Request request) throws IOException {
        byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(request);
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new PayloadTooLargeException("Worker input exceeds its 16 MiB limit");
        }
        writeAtomically(path, bytes);
    }

    public static Request readRequest(Path path) throws IOException {
        long size = Files.size(path);
        if (size > MAX_REQUEST_BYTES) {
            throw new PayloadTooLargeException("Worker input exceeds its 16 MiB limit");
        }
        Request request = OBJECT_MAPPER.readValue(path.toFile(), Request.class);
        validateRequest(request);
        return request;
    }

    public static void writeResult(Path path, CliqueEnumerationResult result) throws IOException {
        byte[] bytes;
        try {
            bytes = encodeResult(result);
        } catch (PayloadTooLargeException oversized) {
            CliqueEnumerationResult limited = new CliqueEnumerationResult(
                    List.of(),
                    CliqueEnumerationStatus.RESULT_SIZE_LIMIT,
                    result.limits(),
                    2,
                    "worker_result_artifact_size_limit",
                    0,
                    java.util.Map.of(),
                    java.util.Map.of(),
                    false);
            bytes = encodeResult(limited);
        }
        writeAtomically(path, bytes);
    }

    public static CliqueEnumerationResult readResult(
            Path path, CliqueEnumerationLimits expectedLimits) throws IOException {
        long size = Files.size(path);
        if (size > MAX_RESULT_ARTIFACT_BYTES) {
            throw new PayloadTooLargeException("Worker result exceeds its 16 MiB file limit");
        }
        byte[] bytes = Files.readAllBytes(path);
        Artifact artifact = OBJECT_MAPPER.readValue(bytes, Artifact.class);
        validateArtifact(artifact, expectedLimits);
        return new CliqueEnumerationResult(
                artifact.cliques(),
                artifact.status(),
                expectedLimits,
                artifact.encodedCliqueArrayBytes(),
                artifact.terminationDetail(),
                artifact.enumeratedCliqueCount(),
                artifact.cliqueSizeDistribution(),
                artifact.nodeParticipationCounts(),
                artifact.membershipListComplete());
    }

    private static byte[] encodeResult(CliqueEnumerationResult result) throws IOException {
        Artifact artifact = new Artifact(
                SCHEMA_VERSION,
                result.status(),
                result.isComplete(),
                result.cliques(),
                result.encodedCliqueArrayBytes(),
                result.terminationDetail(),
                result.enumeratedCliqueCount(),
                result.cliqueSizeDistribution(),
                result.nodeParticipationCounts(),
                result.membershipListComplete());
        byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(artifact);
        if (bytes.length > MAX_RESULT_ARTIFACT_BYTES) {
            throw new PayloadTooLargeException("Worker result exceeds its 16 MiB file limit");
        }
        return bytes;
    }

    private static void validateRequest(Request request) throws IOException {
        if (request == null || request.nodeIds() == null || request.relationships() == null) {
            throw new IOException("Worker input is missing required arrays");
        }
        Set<String> ids = new HashSet<>();
        for (String id : request.nodeIds()) {
            if (id == null || id.isBlank() || !ids.add(id)) {
                throw new IOException("Worker input contains an invalid or duplicate node id");
            }
        }
        for (Relationship relationship : request.relationships()) {
            if (relationship == null
                    || relationship.source() == null || relationship.source().isBlank()
                    || relationship.target() == null || relationship.target().isBlank()) {
                throw new IOException("Worker input contains an invalid relationship");
            }
        }
    }

    private static void validateArtifact(
            Artifact artifact, CliqueEnumerationLimits expectedLimits) throws IOException {
        if (artifact == null
                || artifact.schemaVersion() != SCHEMA_VERSION
                || artifact.status() == null
                || artifact.cliques() == null
                || artifact.cliqueSizeDistribution() == null
                || artifact.nodeParticipationCounts() == null
                || artifact.isComplete() != (artifact.status() == CliqueEnumerationStatus.COMPLETED)) {
            throw new IOException("Worker result has an invalid status envelope");
        }
        if (artifact.cliques().size() > expectedLimits.maxCliques()
                || artifact.enumeratedCliqueCount() < artifact.cliques().size()
                || artifact.enumeratedCliqueCount() > expectedLimits.maxCliques()
                || (artifact.membershipListComplete()
                        && artifact.enumeratedCliqueCount() != artifact.cliques().size())) {
            throw new IOException("Worker result exceeds the configured clique count");
        }

        Set<List<String>> uniqueCliques = new HashSet<>();
        java.util.Map<Integer, Integer> remainingSizes =
                new java.util.TreeMap<>(artifact.cliqueSizeDistribution());
        java.util.Map<String, Integer> remainingParticipation =
                new java.util.TreeMap<>(artifact.nodeParticipationCounts());
        for (List<String> clique : artifact.cliques()) {
            if (clique == null || clique.size() < MaximalCliqueEnumerator.MINIMUM_CLIQUE_SIZE
                    || clique.stream().anyMatch(id -> id == null || id.isBlank())
                    || new HashSet<>(clique).size() != clique.size()
                    || !uniqueCliques.add(clique)) {
                throw new IOException("Worker result contains an invalid clique");
            }
            if (!decrement(remainingSizes, clique.size())) {
                throw new IOException("Worker result clique list exceeds its size summary");
            }
            for (String nodeId : clique) {
                if (!decrement(remainingParticipation, nodeId)) {
                    throw new IOException(
                            "Worker result clique list exceeds its participation summary");
                }
            }
        }
        if (artifact.membershipListComplete()
                && (!remainingSizes.isEmpty()
                        || remainingParticipation.values().stream().anyMatch(count -> count > 0))) {
            throw new IOException("Worker result claims complete membership retention incorrectly");
        }
        long actualArrayBytes = OBJECT_MAPPER.writeValueAsBytes(artifact.cliques()).length;
        if (actualArrayBytes != artifact.encodedCliqueArrayBytes()
                || actualArrayBytes > expectedLimits.maxCliqueArrayBytes()) {
            throw new IOException("Worker result array byte count does not match its payload");
        }
        long distributionTotal = 0;
        for (var entry : artifact.cliqueSizeDistribution().entrySet()) {
            if (entry.getKey() == null
                    || entry.getKey() < MaximalCliqueEnumerator.MINIMUM_CLIQUE_SIZE
                    || entry.getValue() == null
                    || entry.getValue() < 1) {
                throw new IOException("Worker result has an invalid clique size distribution");
            }
            distributionTotal += entry.getValue();
        }
        if (distributionTotal != artifact.enumeratedCliqueCount()) {
            throw new IOException("Worker result clique summary total is invalid");
        }
        long participationTotal = 0;
        for (var entry : artifact.nodeParticipationCounts().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue() < 0) {
                throw new IOException("Worker result has invalid node participation counts");
            }
            participationTotal += entry.getValue();
        }
        long expectedParticipationTotal = artifact.cliqueSizeDistribution().entrySet().stream()
                .mapToLong(entry -> (long) entry.getKey() * entry.getValue())
                .sum();
        if (participationTotal != expectedParticipationTotal) {
            throw new IOException("Worker result participation summary total is invalid");
        }
        if (artifact.status() == CliqueEnumerationStatus.FAILED
                && (artifact.terminationDetail() == null
                        || artifact.terminationDetail().isBlank())) {
            throw new IOException("Worker failure result has no failure type");
        }
    }

    private static void writeAtomically(Path path, byte[] bytes) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".pending");
        try {
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static <K> boolean decrement(java.util.Map<K, Integer> counts, K key) {
        Integer count = counts.get(key);
        if (count == null || count < 1) {
            return false;
        }
        if (count == 1) {
            counts.remove(key);
        } else {
            counts.put(key, count - 1);
        }
        return true;
    }

    public record Request(List<String> nodeIds, List<Relationship> relationships) {
        public Request {
            nodeIds = nodeIds == null ? null : List.copyOf(nodeIds);
            relationships = relationships == null ? null : List.copyOf(relationships);
        }
    }

    public record Relationship(String source, String target) {
    }

    private record Artifact(
            int schemaVersion,
            CliqueEnumerationStatus status,
            boolean isComplete,
            List<List<String>> cliques,
            long encodedCliqueArrayBytes,
            String terminationDetail,
            long enumeratedCliqueCount,
            java.util.Map<Integer, Integer> cliqueSizeDistribution,
            java.util.Map<String, Integer> nodeParticipationCounts,
            boolean membershipListComplete) {
    }

    public static final class PayloadTooLargeException extends IOException {
        public PayloadTooLargeException(String message) {
            super(message);
        }
    }
}
