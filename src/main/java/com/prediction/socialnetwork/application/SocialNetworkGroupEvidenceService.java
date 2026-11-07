package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class SocialNetworkGroupEvidenceService {

    private final SocialNetworkDatasetRegistry registry;
    private final SocialNetworkWorkspaceService workspace;
    private final CliqueEnumerationWorkflowService cliqueWorkflow;
    private final LouvainAnalysisWorkflowService louvainWorkflow;
    private final SocialNetworkDataFingerprintService fingerprintService;

    public SocialNetworkGroupEvidenceService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            CliqueEnumerationWorkflowService cliqueWorkflow,
            LouvainAnalysisWorkflowService louvainWorkflow,
            SocialNetworkDataFingerprintService fingerprintService) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.cliqueWorkflow = Objects.requireNonNull(cliqueWorkflow, "cliqueWorkflow");
        this.louvainWorkflow = Objects.requireNonNull(louvainWorkflow, "louvainWorkflow");
        this.fingerprintService = Objects.requireNonNull(
                fingerprintService, "fingerprintService");
    }

    public SocialNetworkGroupEvidenceView clique(
            String datasetId,
            int cliqueIndex,
            String section,
            int offset,
            int pageSize) {
        validateQuery(cliqueIndex, section, offset, pageSize);
        CliqueEnumerationWorkflowView analysis =
                cliqueWorkflow.status(datasetId, cliqueIndex, 1);
        String targetId = Integer.toString(cliqueIndex);
        if (analysis.dataFingerprint() == null) {
            return unavailable(
                    datasetId, "clique", analysis.state(), false,
                    analysis.membershipListComplete(), null,
                    analysis.terminationDetail(), null, targetId, section, offset, pageSize);
        }
        if (analysis.cliques().isEmpty()) {
            String reason = switch (analysis.state()) {
                case "NOT_REQUESTED" -> "analysis_not_requested";
                case "RUNNING" -> "analysis_running";
                default -> analysis.retainedCliqueCount() <= cliqueIndex
                        ? "clique_index_not_available"
                        : "clique_membership_not_retained";
            };
            return unavailable(
                    datasetId, "clique", analysis.state(), analysis.complete(),
                    analysis.membershipListComplete(), null,
                    reason, analysis.dataFingerprint(),
                    targetId, section, offset, pageSize);
        }
        if (!inputMatches(datasetId, analysis.dataFingerprint())) {
            return unavailable(
                    datasetId, "clique", "FAILED", false,
                    false, null,
                    "input_changed_before_evidence_query", analysis.dataFingerprint(),
                    targetId, section, offset, pageSize);
        }

        SocialNetworkWorkspaceService.GroupEvidenceInput snapshot = workspace.getGroupEvidenceInput(
                datasetId, analysis.dataFingerprint());
        SocialNetworkGraphResult network = snapshot.network();
        CliqueEvidenceAggregator aggregator = new CliqueEvidenceAggregator(
                network.nodes(), snapshot.users(), snapshot.relationships());
        CliqueEvidenceAggregator.EvidencePage page =
                aggregator.aggregateKnownCliqueSection(
                        cliqueIndex,
                        analysis.cliques().get(0),
                        section,
                        offset,
                        pageSize);
        if (!inputMatches(datasetId, analysis.dataFingerprint())) {
            return unavailable(
                    datasetId, "clique", "FAILED", false,
                    false, null,
                    "input_changed_during_evidence_query", analysis.dataFingerprint(),
                    targetId, section, offset, pageSize);
        }
        return available(
                datasetId,
                "clique",
                analysis.state(),
                analysis.complete(),
                analysis.membershipListComplete(),
                null,
                analysis.dataFingerprint(),
                targetId,
                page.section(),
                page.offset(),
                page.pageSize(),
                page.totalCount(),
                page.members(),
                page.ties());
    }

    public SocialNetworkGroupEvidenceView louvain(
            String datasetId,
            String communityId,
            String section,
            int offset,
            int pageSize) {
        validateQuery(0, section, offset, pageSize);
        if (communityId == null || communityId.isBlank()) {
            throw new IllegalArgumentException("communityId must not be blank");
        }
        LouvainAnalysisWorkflowView analysis = louvainWorkflow.status(datasetId);
        if (!analysis.workerCompleted() || analysis.dataFingerprint() == null) {
            return unavailable(
                    datasetId, "louvain", analysis.state(), analysis.complete(),
                    analysis.workerCompleted(), analysis.converged(),
                    analysis.terminationDetail(), analysis.dataFingerprint(),
                    communityId, section, offset, pageSize);
        }

        List<String> members = analysis.communityByNodeId().entrySet().stream()
                .filter(entry -> communityId.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (members.isEmpty()) {
            return unavailable(
                    datasetId, "louvain", analysis.state(), analysis.complete(),
                    analysis.workerCompleted(), analysis.converged(),
                    "community_not_found", analysis.dataFingerprint(),
                    communityId, section, offset, pageSize);
        }
        if (!inputMatches(datasetId, analysis.dataFingerprint())) {
            return unavailable(
                    datasetId, "louvain", "FAILED", false,
                    false, false,
                    "input_changed_before_evidence_query", analysis.dataFingerprint(),
                    communityId, section, offset, pageSize);
        }

        SocialNetworkWorkspaceService.GroupEvidenceInput snapshot = workspace.getGroupEvidenceInput(
                datasetId, analysis.dataFingerprint());
        SocialNetworkGraphResult network = snapshot.network();
        LouvainEvidenceAggregator aggregator = new LouvainEvidenceAggregator(
                network.nodes(), snapshot.users(), snapshot.relationships());
        LouvainEvidenceAggregator.EvidencePage page =
                aggregator.page(members, section, offset, pageSize);
        if (!inputMatches(datasetId, analysis.dataFingerprint())) {
            return unavailable(
                    datasetId, "louvain", "FAILED", false,
                    false, false,
                    "input_changed_during_evidence_query", analysis.dataFingerprint(),
                    communityId, section, offset, pageSize);
        }
        return available(
                datasetId,
                "louvain",
                analysis.state(),
                analysis.complete(),
                analysis.workerCompleted(),
                analysis.converged(),
                analysis.dataFingerprint(),
                communityId,
                page.section(),
                page.offset(),
                page.pageSize(),
                page.totalCount(),
                page.members(),
                page.ties());
    }

    public SocialNetworkGroupEvidenceView betweenCommunities(
            String datasetId, String first, String second, int offset, int pageSize) {
        validateQuery(0, "ties", offset, pageSize);
        if (first == null || first.isBlank() || second == null || second.isBlank()
                || first.equals(second)) {
            throw new IllegalArgumentException("Two distinct community IDs are required");
        }
        LouvainAnalysisWorkflowView analysis = louvainWorkflow.status(datasetId);
        String targetId = first + " / " + second;
        if (!analysis.complete() || !analysis.converged() || analysis.dataFingerprint() == null) {
            return unavailable(datasetId, "louvain", analysis.state(), false,
                    false, analysis.converged(), "complete_partition_unavailable",
                    analysis.dataFingerprint(), targetId, "ties", offset, pageSize);
        }
        List<String> firstMembers = analysis.communityByNodeId().entrySet().stream()
                .filter(entry -> first.equals(entry.getValue())).map(Map.Entry::getKey).toList();
        List<String> secondMembers = analysis.communityByNodeId().entrySet().stream()
                .filter(entry -> second.equals(entry.getValue())).map(Map.Entry::getKey).toList();
        if (firstMembers.isEmpty() || secondMembers.isEmpty()) {
            return unavailable(datasetId, "louvain", analysis.state(), true,
                    true, true, "community_not_found",
                    analysis.dataFingerprint(), targetId, "ties", offset, pageSize);
        }
        if (!inputMatches(datasetId, analysis.dataFingerprint())) {
            return unavailable(datasetId, "louvain", "FAILED", false,
                    false, false, "input_changed_before_evidence_query",
                    analysis.dataFingerprint(), targetId, "ties", offset, pageSize);
        }
        SocialNetworkWorkspaceService.GroupEvidenceInput input =
                workspace.getGroupEvidenceInput(datasetId, analysis.dataFingerprint());
        LouvainEvidenceAggregator aggregator = new LouvainEvidenceAggregator(
                input.network().nodes(), input.users(), input.relationships());
        LouvainEvidenceAggregator.EvidencePage page =
                aggregator.between(firstMembers, secondMembers, offset, pageSize);
        LouvainAnalysisWorkflowView after = louvainWorkflow.status(datasetId);
        if (!inputMatches(datasetId, analysis.dataFingerprint()) || !after.complete()
                || !Objects.equals(analysis.dataFingerprint(), after.dataFingerprint())
                || !Objects.equals(analysis.cacheKey(), after.cacheKey())
                || !Objects.equals(analysis.finishedAt(), after.finishedAt())) {
            return unavailable(datasetId, "louvain", "FAILED", false,
                    false, false, "input_changed_during_evidence_query",
                    analysis.dataFingerprint(), targetId, "ties", offset, pageSize);
        }
        return available(datasetId, "louvain", analysis.state(), true, true, true,
                analysis.dataFingerprint(), targetId, "ties", offset, pageSize,
                page.totalCount(), page.members(), page.ties());
    }

    private boolean inputMatches(String datasetId, String expectedFingerprint) {
        try {
            return expectedFingerprint.equals(fingerprintService.fingerprint(
                    registry.findDataset(datasetId)).dataFingerprint());
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static void validateQuery(
            int targetIndex, String section, int offset, int pageSize) {
        if (targetIndex < 0) {
            throw new IllegalArgumentException("target index must be non-negative");
        }
        if (!"members".equals(section) && !"ties".equals(section)) {
            throw new IllegalArgumentException("section must be members or ties");
        }
        if (offset < 0 || pageSize < 1
                || pageSize > CliqueEvidenceAggregator.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "offset must be non-negative and pageSize must be between 1 and "
                            + CliqueEvidenceAggregator.MAX_PAGE_SIZE);
        }
    }

    private static SocialNetworkGroupEvidenceView available(
            String datasetId,
            String analysisType,
            String state,
            boolean analysisComplete,
            boolean membershipListComplete,
            Boolean converged,
            String dataFingerprint,
            String targetId,
            String section,
            int offset,
            int pageSize,
            int totalCount,
            List<CliqueEvidenceAggregator.MemberEvidence> members,
            List<CliqueEvidenceAggregator.TieEvidence> ties) {
        return new SocialNetworkGroupEvidenceView(
                datasetId,
                analysisType,
                state,
                analysisComplete,
                membershipListComplete,
                converged,
                true,
                null,
                dataFingerprint,
                targetId,
                section,
                offset,
                pageSize,
                totalCount,
                members,
                ties);
    }

    private static SocialNetworkGroupEvidenceView unavailable(
            String datasetId,
            String analysisType,
            String state,
            boolean analysisComplete,
            boolean membershipListComplete,
            Boolean converged,
            String reason,
            String dataFingerprint,
            String targetId,
            String section,
            int offset,
            int pageSize) {
        return new SocialNetworkGroupEvidenceView(
                datasetId,
                analysisType,
                state,
                analysisComplete,
                membershipListComplete,
                converged,
                false,
                reason,
                dataFingerprint,
                targetId,
                section,
                offset,
                pageSize,
                0,
                List.of(),
                List.of());
    }
}
