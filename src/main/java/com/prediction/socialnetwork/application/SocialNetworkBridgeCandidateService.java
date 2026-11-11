package com.prediction.socialnetwork.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.NetworkNodeType;
import com.prediction.socialnetwork.normalization.NormalizedUser;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class SocialNetworkBridgeCandidateService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String FULL_NETWORK_SCOPE = "full_normalized_network";
    private static final ObjectMapper IDENTITY_MAPPER =
            new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final SocialNetworkDatasetRegistry registry;
    private final SocialNetworkWorkspaceService workspace;
    private final LouvainAnalysisWorkflowService louvainWorkflow;
    private final SocialNetworkDataFingerprintService fingerprintService;

    public SocialNetworkBridgeCandidateService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            LouvainAnalysisWorkflowService louvainWorkflow,
            SocialNetworkDataFingerprintService fingerprintService) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.louvainWorkflow = Objects.requireNonNull(louvainWorkflow, "louvainWorkflow");
        this.fingerprintService = Objects.requireNonNull(
                fingerprintService, "fingerprintService");
    }

    public CandidateListView candidates(String datasetId, int offset, int pageSize) {
        return candidates(datasetId, null, null, offset, pageSize);
    }

    public CandidateListView candidates(
            String datasetId,
            String datasetVersion,
            String sessionId,
            int offset,
            int pageSize) {
        validatePage(offset, pageSize);
        Prepared prepared = prepare(datasetId, datasetVersion, sessionId);
        if (!prepared.available()) {
            return unavailableCandidates(datasetId, prepared.reason(), offset, pageSize);
        }
        if (!inputMatches(
                datasetId,
                datasetVersion,
                sessionId,
                prepared.analysis().dataFingerprint())) {
            return unavailableCandidates(
                    datasetId, "input_changed_before_query", offset, pageSize);
        }

        SocialNetworkWorkspaceService.GroupEvidenceInput snapshot = loadSnapshot(
                datasetId, datasetVersion, sessionId, prepared);
        if (snapshot == null) {
            return unavailableCandidates(
                    datasetId, "network_snapshot_unavailable", offset, pageSize);
        }
        List<SocialNetworkBridgeCandidateCalculator.Candidate> candidates =
                SocialNetworkBridgeCandidateCalculator.calculate(
                        snapshot.network(), prepared.analysis().communityByNodeId());
        Map<String, NormalizedUser> profiles = profilesById(snapshot.users());
        if (candidates.stream().anyMatch(candidate -> !profiles.containsKey(candidate.accountId()))) {
            return unavailableCandidates(
                    datasetId, "profile_records_incomplete", offset, pageSize);
        }
        String unavailableReason = currentResultUnavailableReason(
                datasetId, datasetVersion, sessionId, prepared);
        if (unavailableReason != null) {
            return unavailableCandidates(
                    datasetId, unavailableReason, offset, pageSize);
        }

        int start = Math.min(offset, candidates.size());
        int end = (int) Math.min(candidates.size(), (long) start + pageSize);
        List<CandidateItem> items = new java.util.ArrayList<>(end - start);
        for (int index = start; index < end; index++) {
            SocialNetworkBridgeCandidateCalculator.Candidate candidate =
                    candidates.get(index);
            items.add(candidateItem(
                    index + 1, candidate, profiles.get(candidate.accountId())));
        }
        return new CandidateListView(
                datasetId,
                candidates.isEmpty() ? "empty" : "available",
                null,
                FULL_NETWORK_SCOPE,
                prepared.identity(),
                offset,
                pageSize,
                candidates.size(),
                items);
    }

    public EvidenceView evidence(
            String datasetId,
            String accountId,
            String communityId,
            String direction,
            String expectedAnalysisResultId,
            int offset,
            int pageSize) {
        return evidence(
                datasetId,
                null,
                null,
                accountId,
                communityId,
                direction,
                expectedAnalysisResultId,
                offset,
                pageSize);
    }

    public EvidenceView evidence(
            String datasetId,
            String datasetVersion,
            String sessionId,
            String accountId,
            String communityId,
            String direction,
            String expectedAnalysisResultId,
            int offset,
            int pageSize) {
        validatePage(offset, pageSize);
        if (accountId == null || accountId.isBlank()
                || (communityId != null && communityId.isBlank())
                || !("all".equals(direction)
                        || "outgoing".equals(direction)
                        || "incoming".equals(direction))
                || expectedAnalysisResultId == null || expectedAnalysisResultId.isBlank()) {
            throw new IllegalArgumentException("Invalid bridge evidence query");
        }

        Prepared prepared = prepare(datasetId, datasetVersion, sessionId);
        if (!prepared.available()) {
            return unavailableEvidence(
                    datasetId, accountId, communityId, direction,
                    prepared.reason(), offset, pageSize);
        }
        if (!prepared.identity().analysisResultId().equals(expectedAnalysisResultId)) {
            return unavailableEvidence(
                    datasetId, accountId, communityId, direction,
                    "analysis_result_changed", offset, pageSize, prepared.identity());
        }
        if (!inputMatches(
                datasetId,
                datasetVersion,
                sessionId,
                prepared.analysis().dataFingerprint())) {
            return unavailableEvidence(
                    datasetId, accountId, communityId, direction,
                    "input_changed_before_query", offset, pageSize, prepared.identity());
        }

        SocialNetworkWorkspaceService.GroupEvidenceInput snapshot = loadSnapshot(
                datasetId, datasetVersion, sessionId, prepared);
        if (snapshot == null) {
            return unavailableEvidence(
                    datasetId, accountId, communityId, direction,
                    "network_snapshot_unavailable", offset, pageSize);
        }
        List<SocialNetworkBridgeCandidateCalculator.Candidate> candidates =
                SocialNetworkBridgeCandidateCalculator.calculate(
                        snapshot.network(), prepared.analysis().communityByNodeId());
        if (candidates.stream().noneMatch(candidate -> candidate.accountId().equals(accountId))) {
            return unavailableEvidence(
                    datasetId, accountId, communityId, direction,
                    "candidate_not_found", offset, pageSize, prepared.identity());
        }

        SocialNetworkBridgeEvidenceAggregator.Page page =
                SocialNetworkBridgeEvidenceAggregator.page(
                        snapshot.network().nodes(),
                        snapshot.relationships(),
                        prepared.analysis().communityByNodeId(),
                        accountId,
                        communityId,
                        direction,
                        offset,
                        pageSize);
        String unavailableReason = currentResultUnavailableReason(
                datasetId, datasetVersion, sessionId, prepared);
        if (unavailableReason != null) {
            return unavailableEvidence(
                    datasetId, accountId, communityId, direction,
                    unavailableReason, offset, pageSize);
        }
        return new EvidenceView(
                datasetId,
                page.totalCount() == 0 ? "empty" : "available",
                null,
                FULL_NETWORK_SCOPE,
                accountId,
                communityId,
                direction,
                prepared.identity(),
                offset,
                pageSize,
                page.totalCount(),
                page.items());
    }

    private Prepared prepare(
            String datasetId, String datasetVersion, String sessionId) {
        SocialNetworkDataset dataset = datasetVersion == null && sessionId == null
                ? registry.findDataset(datasetId)
                : registry.resolveDatasetContext(datasetId, datasetVersion, sessionId);
        boolean supportedBuiltin = dataset.type() == DatasetType.BUILTIN
                && SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID.equals(dataset.datasetId())
                && SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION
                        .equals(dataset.datasetVersion());
        if (dataset.isUploadSession() && datasetVersion == null && sessionId == null) {
            throw new SocialNetworkDatasetContextException();
        }
        if (!supportedBuiltin && !dataset.isUploadSession()) {
            return Prepared.unavailable("unsupported_dataset_scope");
        }

        LouvainAnalysisWorkflowView analysis = louvainStatus(
                datasetId, datasetVersion, sessionId);
        if (!identityAvailable(analysis, datasetId, dataset.datasetVersion())) {
            return Prepared.unavailable(unavailableReason(analysis));
        }
        return new Prepared(analysis, analysisIdentity(analysis), null);
    }

    private SocialNetworkWorkspaceService.GroupEvidenceInput loadSnapshot(
            String datasetId,
            String datasetVersion,
            String sessionId,
            Prepared prepared) {
        try {
            if (datasetVersion == null && sessionId == null) {
                return workspace.getGroupEvidenceInput(
                        datasetId, prepared.analysis().dataFingerprint());
            }
            return workspace.getGroupEvidenceInput(
                    datasetId,
                    datasetVersion,
                    sessionId,
                    prepared.analysis().dataFingerprint());
        } catch (SocialNetworkDatasetSessionExpiredException exception) {
            throw exception;
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private String currentResultUnavailableReason(
            String datasetId,
            String datasetVersion,
            String sessionId,
            Prepared prepared) {
        LouvainAnalysisWorkflowView current = louvainStatus(
                datasetId, datasetVersion, sessionId);
        SocialNetworkDataset dataset = datasetVersion == null && sessionId == null
                ? registry.findDataset(datasetId)
                : registry.resolveDatasetContext(datasetId, datasetVersion, sessionId);
        if (!identityAvailable(current, datasetId, dataset.datasetVersion())) {
            return "analysis_result_changed";
        }
        if (!prepared.identity().analysisResultId()
                .equals(analysisIdentity(current).analysisResultId())) {
            return "analysis_result_changed";
        }
        return inputMatches(
                datasetId,
                datasetVersion,
                sessionId,
                prepared.analysis().dataFingerprint())
                ? null
                : "input_changed_during_query";
    }

    private boolean inputMatches(
            String datasetId,
            String datasetVersion,
            String sessionId,
            String expectedFingerprint) {
        SocialNetworkDataset dataset = datasetVersion == null && sessionId == null
                ? registry.findDataset(datasetId)
                : registry.resolveDatasetContext(datasetId, datasetVersion, sessionId);
        if (dataset.isUploadSession()) {
            SocialNetworkDataFingerprintService.Fingerprint fingerprint =
                    workspace.get(datasetId, datasetVersion, sessionId).inputFingerprint();
            return fingerprint != null
                    && datasetId.equals(fingerprint.datasetId())
                    && datasetVersion.equals(fingerprint.datasetVersion())
                    && expectedFingerprint.equals(fingerprint.dataFingerprint());
        }
        try {
            return expectedFingerprint.equals(fingerprintService.fingerprint(
                    dataset).dataFingerprint());
        } catch (IOException exception) {
            return false;
        }
    }

    private LouvainAnalysisWorkflowView louvainStatus(
            String datasetId, String datasetVersion, String sessionId) {
        if (datasetVersion == null && sessionId == null) {
            return louvainWorkflow.status(datasetId);
        }
        return louvainWorkflow.status(datasetId, datasetVersion, sessionId);
    }

    private static boolean identityAvailable(
            LouvainAnalysisWorkflowView analysis,
            String datasetId,
            String datasetVersion) {
        return analysis != null
                && analysis.complete()
                && analysis.converged()
                && Objects.equals(datasetId, analysis.datasetId())
                && Objects.equals(datasetVersion, analysis.datasetVersion())
                && FULL_NETWORK_SCOPE.equals(analysis.analysisScope())
                && analysis.dataFingerprint() != null
                && !analysis.dataFingerprint().isBlank()
                && analysis.cacheKey() != null
                && !analysis.cacheKey().isBlank()
                && analysis.algorithmVersion() != null
                && !analysis.algorithmVersion().isBlank();
    }

    private static AnalysisIdentity analysisIdentity(LouvainAnalysisWorkflowView analysis) {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("datasetId", analysis.datasetId());
        canonical.put("datasetVersion", analysis.datasetVersion());
        canonical.put("dataFingerprint", analysis.dataFingerprint());
        canonical.put("cacheKey", analysis.cacheKey());
        canonical.put("algorithmVersion", analysis.algorithmVersion());
        canonical.put("algorithmParameters", new TreeMap<>(analysis.algorithmParameters()));
        canonical.put("communityByNodeId", new TreeMap<>(analysis.communityByNodeId()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(IDENTITY_MAPPER.writeValueAsBytes(canonical));
            return new AnalysisIdentity(
                    "sha256:" + HexFormat.of().formatHex(digest),
                    analysis.datasetId(),
                    analysis.datasetVersion(),
                    analysis.dataFingerprint(),
                    analysis.cacheKey(),
                    analysis.algorithmVersion(),
                    analysis.algorithmParameters());
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Unable to identify the Louvain result", exception);
        }
    }

    private static String unavailableReason(LouvainAnalysisWorkflowView analysis) {
        if (analysis == null) {
            return "analysis_result_unavailable";
        }
        if ("NOT_REQUESTED".equals(analysis.state())) {
            return "analysis_not_requested";
        }
        if ("RUNNING".equals(analysis.state())) {
            return "analysis_running";
        }
        if ("BUSY".equals(analysis.state())) {
            return "analysis_busy";
        }
        if (analysis.workerCompleted() && !analysis.converged()) {
            return "analysis_not_converged";
        }
        if (analysis.complete() && analysis.converged()
                && (!FULL_NETWORK_SCOPE.equals(analysis.analysisScope())
                        || analysis.dataFingerprint() == null
                        || analysis.dataFingerprint().isBlank()
                        || analysis.cacheKey() == null
                        || analysis.cacheKey().isBlank()
                        || analysis.algorithmVersion() == null
                        || analysis.algorithmVersion().isBlank())) {
            return "analysis_identity_unavailable";
        }
        return "complete_partition_unavailable";
    }

    private static Map<String, NormalizedUser> profilesById(List<NormalizedUser> users) {
        Map<String, NormalizedUser> profiles = new TreeMap<>();
        users.stream()
                .filter(user -> user.userId() != null && !user.userId().isBlank())
                .sorted(Comparator.comparing(NormalizedUser::sourceFileName)
                        .thenComparingInt(NormalizedUser::sourceRowNumber))
                .forEach(user -> profiles.putIfAbsent(user.userId(), user));
        return profiles;
    }

    private static CandidateItem candidateItem(
            int rank,
            SocialNetworkBridgeCandidateCalculator.Candidate candidate,
            NormalizedUser profile) {
        return new CandidateItem(
                rank,
                candidate.accountId(),
                profile.fields().getOrDefault("username", ""),
                profile.fields().getOrDefault("display_name", ""),
                "available",
                candidate.communityId(),
                candidate.connectedCommunityCount(),
                candidate.distinctOtherAccountCount(),
                candidate.outgoingRelationshipCount(),
                candidate.incomingRelationshipCount(),
                candidate.directedRelationshipCount(),
                candidate.communities());
    }

    private static void validatePage(int offset, int pageSize) {
        if (offset < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "offset must be non-negative and pageSize must be between 1 and "
                            + MAX_PAGE_SIZE);
        }
    }

    private static CandidateListView unavailableCandidates(
            String datasetId, String reason, int offset, int pageSize) {
        return new CandidateListView(
                datasetId, "unavailable", reason, FULL_NETWORK_SCOPE, null,
                offset, pageSize, 0, List.of());
    }

    private static EvidenceView unavailableEvidence(
            String datasetId,
            String accountId,
            String communityId,
            String direction,
            String reason,
            int offset,
            int pageSize) {
        return unavailableEvidence(
                datasetId, accountId, communityId, direction,
                reason, offset, pageSize, null);
    }

    private static EvidenceView unavailableEvidence(
            String datasetId,
            String accountId,
            String communityId,
            String direction,
            String reason,
            int offset,
            int pageSize,
            AnalysisIdentity identity) {
        return new EvidenceView(
                datasetId, "unavailable", reason, FULL_NETWORK_SCOPE,
                accountId, communityId, direction, identity,
                offset, pageSize, 0, List.of());
    }

    private record Prepared(
            LouvainAnalysisWorkflowView analysis,
            AnalysisIdentity identity,
            String reason) {

        private static Prepared unavailable(String reason) {
            return new Prepared(null, null, reason);
        }

        private boolean available() {
            return reason == null;
        }
    }

    public record AnalysisIdentity(
            String analysisResultId,
            String datasetId,
            String datasetVersion,
            String dataFingerprint,
            String cacheKey,
            String algorithmVersion,
            Map<String, Object> algorithmParameters) {
        public AnalysisIdentity {
            algorithmParameters = Map.copyOf(algorithmParameters);
        }
    }

    public record CandidateListView(
            String datasetId,
            String state,
            String reason,
            String scope,
            AnalysisIdentity analysisIdentity,
            int offset,
            int pageSize,
            int totalCount,
            List<CandidateItem> items) {
        public CandidateListView {
            items = List.copyOf(items);
        }
    }

    public record CandidateItem(
            int rank,
            String accountId,
            String username,
            String displayName,
            String profileState,
            String communityId,
            int connectedCommunityCount,
            int distinctOtherAccountCount,
            int outgoingRelationshipCount,
            int incomingRelationshipCount,
            int directedRelationshipCount,
            List<SocialNetworkBridgeCandidateCalculator.CommunitySummary> communities) {
        public CandidateItem {
            communities = List.copyOf(communities);
        }
    }

    public record EvidenceView(
            String datasetId,
            String state,
            String reason,
            String scope,
            String accountId,
            String communityId,
            String direction,
            AnalysisIdentity analysisIdentity,
            int offset,
            int pageSize,
            int totalCount,
            List<SocialNetworkBridgeEvidenceAggregator.RelationshipEvidence> items) {
        public EvidenceView {
            items = List.copyOf(items);
        }
    }
}
