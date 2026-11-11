package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.network.CliqueEnumerationResult;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SocialNetworkGroupInsightsService {

    private final CliqueEnumerationWorkflowService cliqueWorkflow;
    private final LouvainAnalysisWorkflowService louvainWorkflow;
    private final SocialNetworkWorkspaceService workspace;
    private final SocialNetworkAnalysisResultStore resultStore;

    @Autowired
    public SocialNetworkGroupInsightsService(
            CliqueEnumerationWorkflowService cliqueWorkflow,
            LouvainAnalysisWorkflowService louvainWorkflow,
            SocialNetworkWorkspaceService workspace,
            SocialNetworkAnalysisResultStore resultStore) {
        this.cliqueWorkflow = cliqueWorkflow;
        this.louvainWorkflow = louvainWorkflow;
        this.workspace = workspace;
        this.resultStore = resultStore;
    }

    SocialNetworkGroupInsightsService(
            CliqueEnumerationWorkflowService cliqueWorkflow,
            LouvainAnalysisWorkflowService louvainWorkflow,
            SocialNetworkWorkspaceService workspace) {
        this(
                cliqueWorkflow,
                louvainWorkflow,
                workspace,
                SocialNetworkAnalysisResultStore.disabled());
    }

    public CliqueView clique(String datasetId, int offset, int pageSize) {
        return clique(datasetId, null, null, offset, pageSize);
    }

    public CliqueView clique(
            String datasetId,
            String datasetVersion,
            String sessionId,
            int offset,
            int pageSize) {
        if (offset < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid maximum clique page");
        }
        CliqueEnumerationWorkflowView before =
                cliqueStatus(datasetId, datasetVersion, sessionId);
        Optional<CliqueEnumerationResult> result = completedMemberships(
                datasetId, datasetVersion, sessionId);
        if (!before.complete() || !before.membershipListComplete() || result.isEmpty()) {
            return new CliqueView(false, "complete_memberships_unavailable",
                    before.dataFingerprint(), Map.of(), null);
        }
        SocialNetworkGroupInsightCalculator.CliqueInsights insights =
                SocialNetworkGroupInsightCalculator.clique(result.get(), offset, pageSize);
        Map<String, String> names = datasetVersion == null && sessionId == null
                ? workspace.analysisProfileNames(datasetId)
                : workspace.analysisProfileNames(datasetId, datasetVersion, sessionId);
        CliqueEnumerationWorkflowView after =
                cliqueStatus(datasetId, datasetVersion, sessionId);
        if (!sameCliqueResult(before, after)) {
            return new CliqueView(false, "analysis_changed", after.dataFingerprint(), Map.of(), null);
        }
        return new CliqueView(true, null, before.dataFingerprint(), names, insights);
    }

    public LouvainView louvain(String datasetId) {
        return louvain(datasetId, null, null);
    }

    public LouvainView louvain(
            String datasetId, String datasetVersion, String sessionId) {
        LouvainAnalysisWorkflowView before =
                louvainStatus(datasetId, datasetVersion, sessionId);
        if (!before.complete() || !before.converged() || before.dataFingerprint() == null) {
            return new LouvainView(false, "complete_partition_unavailable",
                    before.dataFingerprint(), Map.of(), List.of());
        }
        boolean persistable = SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID
                        .equals(before.datasetId())
                && SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION
                        .equals(before.datasetVersion());
        List<SocialNetworkGroupInsightCalculator.CommunityPair> pairs =
                persistable
                        ? resultStore.loadCommunityPairs(
                                before.datasetId(),
                                before.datasetVersion(),
                                before.cacheKey(),
                                before.dataFingerprint())
                                .orElse(null)
                        : null;
        if (pairs == null) {
            SocialNetworkGraphResult graph;
            if (persistable) {
                Optional<SocialNetworkGraphResult> cachedGraph =
                        workspace.cachedNetworkForAnalysis(
                                datasetId, before.dataFingerprint());
                if (cachedGraph.isPresent()) {
                    graph = cachedGraph.get();
                } else {
                    try {
                        graph = workspace.getGroupEvidenceInput(
                                datasetId, before.dataFingerprint()).network();
                    } catch (IllegalStateException exception) {
                        if (exception instanceof SocialNetworkDatasetSessionExpiredException) {
                            throw exception;
                        }
                        return new LouvainView(false, "analysis_graph_unavailable",
                                before.dataFingerprint(), Map.of(), List.of());
                    }
                }
            } else {
                try {
                    graph = workspace.getGroupEvidenceInput(
                            datasetId,
                            datasetVersion,
                            sessionId,
                            before.dataFingerprint()).network();
                } catch (IllegalStateException exception) {
                    if (exception instanceof SocialNetworkDatasetSessionExpiredException) {
                        throw exception;
                    }
                    return new LouvainView(false, "analysis_graph_unavailable",
                            before.dataFingerprint(), Map.of(), List.of());
                }
            }
            pairs = SocialNetworkGroupInsightCalculator.communityPairs(
                    graph.edges(), before.communityByNodeId());
        }
        Map<String, String> names = datasetVersion == null && sessionId == null
                ? workspace.analysisProfileNames(datasetId)
                : workspace.analysisProfileNames(datasetId, datasetVersion, sessionId);
        LouvainAnalysisWorkflowView after =
                louvainStatus(datasetId, datasetVersion, sessionId);
        if (!sameLouvainResult(before, after)) {
            return new LouvainView(false, "analysis_changed",
                    after.dataFingerprint(), Map.of(), List.of());
        }
        if (before.quality() == null || pairs.stream()
                .mapToLong(SocialNetworkGroupInsightCalculator.CommunityPair::tieCount)
                .sum() != before.quality().crossCommunityTieCount()) {
            return new LouvainView(false, "partition_graph_mismatch",
                    before.dataFingerprint(), Map.of(), List.of());
        }
        if (persistable) {
            resultStore.saveCommunityPairs(
                    before.datasetId(),
                    before.datasetVersion(),
                    before.cacheKey(),
                    before.dataFingerprint(),
                    pairs);
        }
        return new LouvainView(true, null, before.dataFingerprint(), names, pairs);
    }

    public SharedView sharedCliques(
            String datasetId, String first, String second, int offset, int pageSize) {
        return sharedCliques(datasetId, null, null, first, second, offset, pageSize);
    }

    public SharedView sharedCliques(
            String datasetId,
            String datasetVersion,
            String sessionId,
            String first,
            String second,
            int offset,
            int pageSize) {
        if (first == null || first.isBlank() || second == null || second.isBlank()
                || first.equals(second) || offset < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid shared clique query");
        }
        CliqueEnumerationWorkflowView before =
                cliqueStatus(datasetId, datasetVersion, sessionId);
        Optional<CliqueEnumerationResult> result = completedMemberships(
                datasetId, datasetVersion, sessionId);
        if (!before.complete() || !before.membershipListComplete() || result.isEmpty()) {
            return new SharedView(false, "complete_memberships_unavailable",
                    before.dataFingerprint(), null);
        }
        var page = SocialNetworkGroupInsightCalculator.sharedCliques(
                result.get(), first, second, offset, pageSize);
        CliqueEnumerationWorkflowView after =
                cliqueStatus(datasetId, datasetVersion, sessionId);
        if (!sameCliqueResult(before, after)) {
            return new SharedView(false, "analysis_changed", after.dataFingerprint(), null);
        }
        return new SharedView(true, null, before.dataFingerprint(), page);
    }

    private CliqueEnumerationWorkflowView cliqueStatus(
            String datasetId, String datasetVersion, String sessionId) {
        if (datasetVersion == null && sessionId == null) {
            return cliqueWorkflow.status(datasetId, 0, 1);
        }
        return cliqueWorkflow.status(datasetId, datasetVersion, sessionId, 0, 1);
    }

    private Optional<CliqueEnumerationResult> completedMemberships(
            String datasetId, String datasetVersion, String sessionId) {
        if (datasetVersion == null && sessionId == null) {
            return cliqueWorkflow.completedMemberships(datasetId);
        }
        return cliqueWorkflow.completedMemberships(
                datasetId, datasetVersion, sessionId);
    }

    private static boolean sameCliqueResult(
            CliqueEnumerationWorkflowView before, CliqueEnumerationWorkflowView after) {
        return after.complete() && after.membershipListComplete()
                && Objects.equals(before.cacheKey(), after.cacheKey())
                && Objects.equals(before.dataFingerprint(), after.dataFingerprint())
                && Objects.equals(before.requestedAt(), after.requestedAt())
                && Objects.equals(before.finishedAt(), after.finishedAt());
    }

    private static boolean sameLouvainResult(
            LouvainAnalysisWorkflowView before, LouvainAnalysisWorkflowView after) {
        return after.complete() && after.converged()
                && Objects.equals(before.datasetId(), after.datasetId())
                && Objects.equals(before.datasetVersion(), after.datasetVersion())
                && Objects.equals(before.cacheKey(), after.cacheKey())
                && Objects.equals(before.dataFingerprint(), after.dataFingerprint())
                && Objects.equals(before.requestedAt(), after.requestedAt())
                && Objects.equals(before.finishedAt(), after.finishedAt());
    }

    private LouvainAnalysisWorkflowView louvainStatus(
            String datasetId, String datasetVersion, String sessionId) {
        if (datasetVersion == null && sessionId == null) {
            return louvainWorkflow.status(datasetId);
        }
        return louvainWorkflow.status(datasetId, datasetVersion, sessionId);
    }

    public record CliqueView(
            boolean available, String reason, String dataFingerprint,
            Map<String, String> names, SocialNetworkGroupInsightCalculator.CliqueInsights insight) {
    }

    public record LouvainView(
            boolean available, String reason, String dataFingerprint,
            Map<String, String> names,
            List<SocialNetworkGroupInsightCalculator.CommunityPair> communityPairs) {
    }

    public record SharedView(
            boolean available, String reason, String dataFingerprint,
            SocialNetworkGroupInsightCalculator.SharedCliques page) {
    }
}
