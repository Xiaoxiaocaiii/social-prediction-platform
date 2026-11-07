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
        if (offset < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid maximum clique page");
        }
        CliqueEnumerationWorkflowView before = cliqueWorkflow.status(datasetId, 0, 1);
        Optional<CliqueEnumerationResult> result = cliqueWorkflow.completedMemberships(datasetId);
        if (!before.complete() || !before.membershipListComplete() || result.isEmpty()) {
            return new CliqueView(false, "complete_memberships_unavailable",
                    before.dataFingerprint(), Map.of(), null);
        }
        SocialNetworkGroupInsightCalculator.CliqueInsights insights =
                SocialNetworkGroupInsightCalculator.clique(result.get(), offset, pageSize);
        Map<String, String> names = workspace.analysisProfileNames(datasetId);
        CliqueEnumerationWorkflowView after = cliqueWorkflow.status(datasetId, 0, 1);
        if (!sameCliqueResult(before, after)) {
            return new CliqueView(false, "analysis_changed", after.dataFingerprint(), Map.of(), null);
        }
        return new CliqueView(true, null, before.dataFingerprint(), names, insights);
    }

    public LouvainView louvain(String datasetId) {
        LouvainAnalysisWorkflowView before = louvainWorkflow.status(datasetId);
        if (!before.complete() || !before.converged() || before.dataFingerprint() == null) {
            return new LouvainView(false, "complete_partition_unavailable",
                    before.dataFingerprint(), Map.of(), List.of());
        }
        List<SocialNetworkGroupInsightCalculator.CommunityPair> pairs =
                resultStore.loadCommunityPairs(
                        before.datasetId(),
                        before.datasetVersion(),
                        before.cacheKey(),
                        before.dataFingerprint())
                .orElse(null);
        if (pairs == null) {
            Optional<SocialNetworkGraphResult> graph =
                    workspace.cachedNetworkForAnalysis(datasetId, before.dataFingerprint());
            if (graph.isEmpty()) {
                try {
                    graph = Optional.of(workspace.getGroupEvidenceInput(
                            datasetId, before.dataFingerprint()).network());
                } catch (IllegalStateException exception) {
                    return new LouvainView(false, "analysis_graph_unavailable",
                            before.dataFingerprint(), Map.of(), List.of());
                }
            }
            pairs = SocialNetworkGroupInsightCalculator.communityPairs(
                    graph.orElseThrow().edges(), before.communityByNodeId());
        }
        Map<String, String> names = workspace.analysisProfileNames(datasetId);
        LouvainAnalysisWorkflowView after = louvainWorkflow.status(datasetId);
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
        resultStore.saveCommunityPairs(
                before.datasetId(),
                before.datasetVersion(),
                before.cacheKey(),
                before.dataFingerprint(),
                pairs);
        return new LouvainView(true, null, before.dataFingerprint(), names, pairs);
    }

    public SharedView sharedCliques(
            String datasetId, String first, String second, int offset, int pageSize) {
        if (first == null || first.isBlank() || second == null || second.isBlank()
                || first.equals(second) || offset < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid shared clique query");
        }
        CliqueEnumerationWorkflowView before = cliqueWorkflow.status(datasetId, 0, 1);
        Optional<CliqueEnumerationResult> result = cliqueWorkflow.completedMemberships(datasetId);
        if (!before.complete() || !before.membershipListComplete() || result.isEmpty()) {
            return new SharedView(false, "complete_memberships_unavailable",
                    before.dataFingerprint(), null);
        }
        var page = SocialNetworkGroupInsightCalculator.sharedCliques(
                result.get(), first, second, offset, pageSize);
        CliqueEnumerationWorkflowView after = cliqueWorkflow.status(datasetId, 0, 1);
        if (!sameCliqueResult(before, after)) {
            return new SharedView(false, "analysis_changed", after.dataFingerprint(), null);
        }
        return new SharedView(true, null, before.dataFingerprint(), page);
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
                && Objects.equals(before.cacheKey(), after.cacheKey())
                && Objects.equals(before.dataFingerprint(), after.dataFingerprint())
                && Objects.equals(before.requestedAt(), after.requestedAt())
                && Objects.equals(before.finishedAt(), after.finishedAt());
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
