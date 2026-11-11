package com.prediction.socialnetwork.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class SocialNetworkAnalysisResultStore {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SocialNetworkAnalysisResultStore.class);
    private static final int SCHEMA_VERSION = 1;
    private static final String CLIQUE_FILE = "clique-v1.json";
    private static final String LOUVAIN_FILE = "louvain-v1.json";
    private static final String GROUP_EVIDENCE_FILE = "group-evidence-v1.json";
    private static final String COMMUNITY_PAIRS_FILE = "louvain-community-pairs-v2.json";
    private static final long MAX_GROUP_ARTIFACT_BYTES = 32L * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final Path directory;
    private final boolean enabled;

    @Autowired
    public SocialNetworkAnalysisResultStore(
            ObjectMapper objectMapper,
            @Value("${app.analysis-results-directory:}") String configuredDirectory,
            @Value("${app.analysis-results-enabled:true}") boolean enabled) {
        this(
                objectMapper,
                configuredDirectory == null || configuredDirectory.isBlank()
                        ? defaultDirectory()
                        : Path.of(configuredDirectory),
                enabled);
    }

    SocialNetworkAnalysisResultStore(ObjectMapper objectMapper, Path directory) {
        this(objectMapper, directory, true);
    }

    private SocialNetworkAnalysisResultStore(
            ObjectMapper objectMapper, Path directory, boolean enabled) {
        this.objectMapper = objectMapper;
        this.directory = directory;
        this.enabled = enabled;
    }

    static SocialNetworkAnalysisResultStore disabled() {
        return new SocialNetworkAnalysisResultStore(null, (Path) null, false);
    }

    public Optional<CliqueEnumerationWorkflowView> loadClique(String expectedCacheKey) {
        return load(
                CLIQUE_FILE,
                CliqueEnumerationWorkflowView.class,
                view -> isCompleteClique(view)
                        && expectedCacheKey.equals(view.cacheKey()));
    }

    public Optional<LouvainAnalysisWorkflowView> loadLouvain(String expectedCacheKey) {
        return load(
                LOUVAIN_FILE,
                LouvainAnalysisWorkflowView.class,
                view -> isCompleteLouvain(view)
                        && expectedCacheKey.equals(view.cacheKey()));
    }

    public void saveClique(CliqueEnumerationWorkflowView view) {
        if (isCompleteClique(view)) {
            save(CLIQUE_FILE, view);
        }
    }

    public void saveLouvain(LouvainAnalysisWorkflowView view) {
        if (isCompleteLouvain(view)) {
            save(LOUVAIN_FILE, view);
        }
    }

    public Optional<SocialNetworkWorkspaceService.GroupEvidenceInput> loadGroupEvidenceInput(
            String datasetId, String datasetVersion, String dataFingerprint) {
        if (!isPersistedBuiltin(datasetId, datasetVersion) || dataFingerprint == null) {
            return Optional.empty();
        }
        return load(
                        GROUP_EVIDENCE_FILE,
                        GroupEvidenceArtifact.class,
                        artifact -> artifact != null
                                && datasetId.equals(artifact.datasetId())
                                && datasetVersion.equals(artifact.datasetVersion())
                                && dataFingerprint.equals(artifact.dataFingerprint())
                                && artifact.input() != null)
                .map(GroupEvidenceArtifact::input);
    }

    public void saveGroupEvidenceInput(
            String datasetId,
            String datasetVersion,
            String dataFingerprint,
            SocialNetworkWorkspaceService.GroupEvidenceInput input) {
        if (!isPersistedBuiltin(datasetId, datasetVersion) || dataFingerprint == null
                || input == null) {
            return;
        }
        saveBounded(
                GROUP_EVIDENCE_FILE,
                new GroupEvidenceArtifact(
                        datasetId, datasetVersion, dataFingerprint, input));
    }

    public Optional<List<SocialNetworkGroupInsightCalculator.CommunityPair>>
            loadCommunityPairs(
                    String datasetId,
                    String datasetVersion,
                    String cacheKey,
                    String dataFingerprint) {
        if (!isPersistedBuiltin(datasetId, datasetVersion)
                || cacheKey == null || dataFingerprint == null) {
            return Optional.empty();
        }
        return load(
                        COMMUNITY_PAIRS_FILE,
                        CommunityPairsArtifact.class,
                        artifact -> artifact != null
                                && datasetId.equals(artifact.datasetId())
                                && datasetVersion.equals(artifact.datasetVersion())
                                && cacheKey.equals(artifact.cacheKey())
                                && dataFingerprint.equals(artifact.dataFingerprint())
                                && artifact.communityPairs() != null)
                .map(CommunityPairsArtifact::communityPairs);
    }

    public void saveCommunityPairs(
            String datasetId,
            String datasetVersion,
            String cacheKey,
            String dataFingerprint,
            List<SocialNetworkGroupInsightCalculator.CommunityPair> communityPairs) {
        if (!isPersistedBuiltin(datasetId, datasetVersion) || cacheKey == null
                || dataFingerprint == null || communityPairs == null) {
            return;
        }
        saveBounded(
                COMMUNITY_PAIRS_FILE,
                new CommunityPairsArtifact(
                        datasetId,
                        datasetVersion,
                        cacheKey,
                        dataFingerprint,
                        List.copyOf(communityPairs)));
    }

    private static boolean isPersistedBuiltin(String datasetId, String datasetVersion) {
        return SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID.equals(datasetId)
                && SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION.equals(datasetVersion);
    }

    private <T> Optional<T> load(
            String filename,
            Class<T> viewType,
            Predicate<T> validator) {
        if (!enabled) {
            return Optional.empty();
        }
        Path file = directory.resolve(filename);
        if (!Files.isRegularFile(file)
                || isGroupArtifact(filename) && oversized(file)) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(file.toFile());
            JsonNode viewNode = root;
            if (root.has("schemaVersion")) {
                if (root.path("schemaVersion").asInt(-1) != SCHEMA_VERSION) {
                    return Optional.empty();
                }
                viewNode = root.path("view");
            }
            T view = objectMapper.treeToValue(viewNode, viewType);
            return validator.test(view) ? Optional.of(view) : Optional.empty();
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Ignoring unreadable social-network analysis result {}", file, exception);
            return Optional.empty();
        }
    }

    private void save(String filename, Object view) {
        if (!enabled) {
            return;
        }
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(filename);
            temporary = Files.createTempFile(directory, filename, ".tmp");
            objectMapper.writeValue(
                    temporary.toFile(),
                    new PersistedArtifact(SCHEMA_VERSION, view));
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn(
                    "Could not persist social-network analysis result {}",
                    filename,
                    exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    LOGGER.warn("Could not remove temporary analysis result {}", temporary);
                }
            }
        }
    }

    private void saveBounded(String filename, Object view) {
        if (!enabled) {
            return;
        }
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(filename);
            temporary = Files.createTempFile(directory, filename, ".tmp");
            objectMapper.writeValue(temporary.toFile(), new PersistedArtifact(SCHEMA_VERSION, view));
            if (Files.size(temporary) > MAX_GROUP_ARTIFACT_BYTES) {
                LOGGER.warn(
                        "Refusing oversized social-network group artifact {}",
                        filename);
                return;
            }
            moveIntoPlace(temporary, target);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn(
                    "Could not persist social-network group artifact {}",
                    filename,
                    exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    LOGGER.warn("Could not remove temporary group artifact {}", temporary);
                }
            }
        }
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isGroupArtifact(String filename) {
        return GROUP_EVIDENCE_FILE.equals(filename)
                || COMMUNITY_PAIRS_FILE.equals(filename);
    }

    private static boolean oversized(Path file) {
        try {
            return Files.size(file) > MAX_GROUP_ARTIFACT_BYTES;
        } catch (IOException exception) {
            return true;
        }
    }

    private static boolean isCompleteClique(CliqueEnumerationWorkflowView view) {
        return view != null
                && "think-tank-social-network".equals(view.datasetId())
                && "v1".equals(view.datasetVersion())
                && "COMPLETED".equals(view.state())
                && view.complete()
                && view.membershipListComplete()
                && view.retainedCliqueCount() == view.enumeratedCliqueCount()
                && view.retainedCliqueCount() == view.cliques().size()
                && view.dataFingerprint() != null
                && view.cacheKey() != null;
    }

    private static boolean isCompleteLouvain(LouvainAnalysisWorkflowView view) {
        return view != null
                && "think-tank-social-network".equals(view.datasetId())
                && "v1".equals(view.datasetVersion())
                && "COMPLETED".equals(view.state())
                && view.complete()
                && view.workerCompleted()
                && view.converged()
                && view.quality() != null
                && view.quality().inputNodeCount() == view.inputNodeCount()
                && view.quality().assignedNodeCount()
                        == view.communityByNodeId().size()
                && view.communities().stream()
                        .mapToInt(community -> community.nodeCount())
                        .sum() == view.quality().assignedNodeCount()
                && view.quality().unassignedNodeCount() == view.unassignedNodeCount()
                && view.dataFingerprint() != null
                && view.cacheKey() != null;
    }

    private static Path defaultDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(
                    localAppData,
                    "social-prediction-platform",
                    "analysis-results");
        }
        return Path.of(
                System.getProperty("user.home"),
                ".social-prediction-platform",
                "analysis-results");
    }

    private record PersistedArtifact(int schemaVersion, Object view) {
    }

    private record GroupEvidenceArtifact(
            String datasetId,
            String datasetVersion,
            String dataFingerprint,
            SocialNetworkWorkspaceService.GroupEvidenceInput input) {
    }

    private record CommunityPairsArtifact(
            String datasetId,
            String datasetVersion,
            String cacheKey,
            String dataFingerprint,
            List<SocialNetworkGroupInsightCalculator.CommunityPair> communityPairs) {
    }
}
