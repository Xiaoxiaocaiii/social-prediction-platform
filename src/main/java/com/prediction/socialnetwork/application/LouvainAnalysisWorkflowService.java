package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.cache.CacheResultType;
import com.prediction.socialnetwork.cache.SocialNetworkAnalysisCacheKeyFactory;
import com.prediction.socialnetwork.cache.SocialNetworkAnalysisCacheKeyFactory.Identity;
import com.prediction.socialnetwork.cache.SocialNetworkAnalysisResultCache;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.isolation.LouvainWorkerRunResult;
import com.prediction.socialnetwork.isolation.LouvainWorkerRunResult.Status;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
import com.prediction.socialnetwork.network.LouvainAnalysisWorkerProtocol;
import com.prediction.socialnetwork.network.LouvainCommunityDetector;
import com.prediction.socialnetwork.network.LouvainQualityCalculator;
import com.prediction.socialnetwork.network.LouvainQualitySummary;
import com.prediction.socialnetwork.network.CommunityDetectionResult;
import com.prediction.socialnetwork.network.CommunityDetectionStatus;
import com.prediction.socialnetwork.network.NetworkCommunity;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LouvainAnalysisWorkflowService {

    private final SocialNetworkDatasetRegistry registry;
    private final SocialNetworkWorkspaceService workspace;
    private final LouvainAnalysisExecutor runner;
    private final SocialNetworkDataFingerprintService fingerprintService;
    private final SocialNetworkAnalysisCacheKeyFactory cacheKeyFactory;
    private final SocialNetworkAnalysisResultCache resultCache;
    private final SocialNetworkAnalysisResultStore resultStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "social-network-louvain-workflow");
        thread.setDaemon(true);
        return thread;
    });
    private final Object stateLock = new Object();

    private ActiveRun activeRun;

    @Autowired
    public LouvainAnalysisWorkflowService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            LouvainAnalysisExecutor runner,
            SocialNetworkDataFingerprintService fingerprintService,
            SocialNetworkAnalysisCacheKeyFactory cacheKeyFactory,
            SocialNetworkAnalysisResultCache resultCache,
            SocialNetworkAnalysisResultStore resultStore) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.fingerprintService = Objects.requireNonNull(
                fingerprintService, "fingerprintService");
        this.cacheKeyFactory = Objects.requireNonNull(cacheKeyFactory, "cacheKeyFactory");
        this.resultCache = Objects.requireNonNull(resultCache, "resultCache");
        this.resultStore = Objects.requireNonNull(resultStore, "resultStore");
    }

    public LouvainAnalysisWorkflowView request(String datasetId) {
        return request(datasetId, false);
    }

    public LouvainAnalysisWorkflowView request(String datasetId, boolean force) {
        return request(datasetId, null, null, force);
    }

    public LouvainAnalysisWorkflowView request(
            String datasetId, String datasetVersion, String sessionId, boolean force) {
        Identity identity = currentIdentity(datasetId, datasetVersion, sessionId);
        if (identity == null) {
            return buildView(
                    null, "FAILED", null, 0, 0, null, null, false,
                    "input_fingerprint_unavailable");
        }
        if (!force) {
            synchronized (stateLock) {
                if (activeRun != null
                        && "RUNNING".equals(activeRun.state)
                        && sameKey(activeRun.identity, identity)) {
                    return activeView(activeRun);
                }
            }
            Optional<SocialNetworkAnalysisResultCache.Hit<LouvainWorkflowCacheValue>> cached =
                    cached(identity);
            if (cached.isPresent()) {
                LouvainWorkflowCacheValue value = cached.get().result();
                return buildView(
                        identity,
                        state(value.result()),
                        value.result(),
                        value.inputNodeCount(),
                        value.inputDirectedRelationshipCount(),
                        value.requestedAt(),
                        value.finishedAt(),
                        true,
                        value.result().terminationDetail());
            }
        } else {
            cached(identity);
        }

        synchronized (stateLock) {
            if (activeRun != null && "RUNNING".equals(activeRun.state)) {
                if (sameKey(activeRun.identity, identity)) {
                    return activeView(activeRun);
                }
                return buildView(
                        identity, "BUSY", null, 0, 0, null, null, false,
                        "analysis_already_running");
            }
            ActiveRun run = new ActiveRun(identity, Instant.now());
            activeRun = run;
            try {
                executor.execute(() -> calculate(run));
            } catch (RejectedExecutionException exception) {
                finish(run, failedResult("workflow_executor_unavailable"), false);
            }
            return activeView(run);
        }
    }

    public LouvainAnalysisWorkflowView status(String datasetId) {
        return status(datasetId, null, null);
    }

    public LouvainAnalysisWorkflowView status(
            String datasetId, String datasetVersion, String sessionId) {
        return readOnlySnapshot(datasetId, datasetVersion, sessionId).status();
    }

    /**
     * Reads the current status and reusable result against one input identity.
     */
    public ReadOnlySnapshot readOnlySnapshot(String datasetId) {
        return readOnlySnapshot(datasetId, null, null);
    }

    public ReadOnlySnapshot readOnlySnapshot(
            String datasetId, String datasetVersion, String sessionId) {
        Identity identity = currentIdentity(datasetId, datasetVersion, sessionId);
        if (identity == null) {
            LouvainAnalysisWorkflowView failed = buildView(
                    null, "FAILED", null, 0, 0, null, Instant.now(), false,
                    "input_fingerprint_unavailable");
            return new ReadOnlySnapshot(failed, Optional.empty());
        }
        LouvainAnalysisWorkflowView view = currentView(identity);
        Optional<LouvainAnalysisWorkflowView> reusable =
                isReusableResult(view, identity) ? Optional.of(view) : Optional.empty();
        return new ReadOnlySnapshot(view, reusable);
    }

    /**
     * Returns only a complete result for the current input identity; this never starts analysis.
     */
    public Optional<LouvainAnalysisWorkflowView> reusableResult(String datasetId) {
        return readOnlySnapshot(datasetId).reusableResult();
    }

    public Optional<LouvainAnalysisWorkflowView> reusableResult(
            String datasetId, String datasetVersion, String sessionId) {
        return readOnlySnapshot(datasetId, datasetVersion, sessionId).reusableResult();
    }

    private LouvainAnalysisWorkflowView currentView(Identity identity) {
        synchronized (stateLock) {
            if (activeRun != null
                    && "RUNNING".equals(activeRun.state)
                    && sameKey(activeRun.identity, identity)) {
                return activeView(activeRun);
            }
        }
        Optional<SocialNetworkAnalysisResultCache.Hit<LouvainWorkflowCacheValue>> cached =
                cached(identity);
        if (cached.isPresent()) {
            LouvainWorkflowCacheValue value = cached.get().result();
            return buildView(
                    identity,
                    state(value.result()),
                    value.result(),
                    value.inputNodeCount(),
                    value.inputDirectedRelationshipCount(),
                    value.requestedAt(),
                    value.finishedAt(),
                    true,
                    value.result().terminationDetail());
        }
        synchronized (stateLock) {
            if (activeRun != null && sameKey(activeRun.identity, identity)) {
                return activeView(activeRun);
            }
        }
        return buildView(identity, "NOT_REQUESTED", null, 0, 0, null, null, false, null);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private void calculate(ActiveRun run) {
        try {
            SocialNetworkGraphResult network = networkFor(run.identity);
            if (!fingerprintMatches(run.identity)) {
                finish(run, failedResult("input_changed_during_snapshot_load"), false);
                return;
            }
            List<NetworkNode> nodes = network.nodes();
            List<NetworkEdge> directedEdges = network.edges();
            synchronized (stateLock) {
                run.inputNodeCount = nodes.size();
                run.inputDirectedRelationshipCount = directedEdges.size();
            }

            LouvainWorkerRunResult result = runner.run(
                    nodes,
                    directedEdges,
                    LouvainAnalysisParameters.DEFAULT,
                    Thread.currentThread()::isInterrupted);
            if (!fingerprintMatches(run.identity)) {
                finish(run, failedResult("input_changed_during_analysis", result), false);
                return;
            }
            finish(run, result, result.status() == Status.COMPLETED);
        } catch (RuntimeException exception) {
            finish(run, failedResult("workflow_input_or_runner_failure"), false);
        }
    }

    private void finish(ActiveRun run, LouvainWorkerRunResult result, boolean cacheable) {
        synchronized (stateLock) {
            run.result = Objects.requireNonNull(result, "result");
            run.state = state(result);
            run.finishedAt = Instant.now();
            boolean previousComplete = resultCache.find(
                            CacheResultType.LOUVAIN_ANALYSIS,
                            run.identity.cacheKey(),
                            LouvainWorkflowCacheValue.class)
                    .map(hit -> hit.result().result().status() == Status.COMPLETED
                            && hit.result().result().detection().converged()
                            && hit.result().result().quality() != null)
                    .orElse(false);
            boolean currentComplete = result.status() == Status.COMPLETED
                    && result.detection().converged()
                    && result.quality() != null;
            boolean uploadSessionActive = !isUploadIdentity(run.identity)
                    || fingerprintMatches(run.identity);
            if (cacheable && uploadSessionActive && (!previousComplete || currentComplete)) {
                resultCache.put(
                        CacheResultType.LOUVAIN_ANALYSIS,
                        run.identity.cacheKey(),
                        run.identity.sessionId(),
                        new LouvainWorkflowCacheValue(
                                result,
                                run.inputNodeCount,
                                run.inputDirectedRelationshipCount,
                                run.requestedAt,
                                run.finishedAt),
                        run.finishedAt);
            }
            if (isUploadIdentity(run.identity) && !fingerprintMatches(run.identity)) {
                resultCache.removeSession(run.identity.sessionId());
            }
        }
        if (!isUploadIdentity(run.identity)) {
            resultStore.saveLouvain(buildView(
                    run.identity,
                    state(result),
                    result,
                    run.inputNodeCount,
                    run.inputDirectedRelationshipCount,
                    run.requestedAt,
                    run.finishedAt,
                    false,
                    result.terminationDetail()));
        }
    }

    private LouvainAnalysisWorkflowView activeView(ActiveRun run) {
        return buildView(
                run.identity,
                run.state,
                run.result,
                run.inputNodeCount,
                run.inputDirectedRelationshipCount,
                run.requestedAt,
                run.finishedAt,
                false,
                run.result == null ? null : run.result.terminationDetail());
    }

    private LouvainAnalysisWorkflowView buildView(
            Identity identity,
            String state,
            LouvainWorkerRunResult result,
            int inputNodeCount,
            int inputDirectedRelationshipCount,
            Instant requestedAt,
            Instant finishedAt,
            boolean cacheHit,
            String detail) {
        boolean workerCompleted = result != null && result.status() == Status.COMPLETED;
        boolean converged = workerCompleted && result.detection().converged();
        LouvainQualitySummary quality = workerCompleted ? result.quality() : null;
        boolean fullNodeAccounting = quality != null
                && quality.assignedNodeCount() + quality.unassignedNodeCount()
                        == inputNodeCount;
        boolean complete = workerCompleted && converged && fullNodeAccounting;
        List<NetworkCommunity> communities = workerCompleted
                ? result.detection().communities()
                : List.of();
        Map<String, String> membership = workerCompleted
                ? result.detection().communityByNodeId()
                : Map.of();

        Map<String, Object> parameters = identity == null
                ? defaultAlgorithmParameters()
                : identity.parameters();
        return new LouvainAnalysisWorkflowView(
                identity == null
                        ? SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID
                        : identity.datasetId(),
                identity == null
                        ? SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION
                        : identity.datasetVersion(),
                state,
                complete,
                workerCompleted,
                converged,
                result == null ? state : result.status().name(),
                detail,
                requestedAt,
                finishedAt,
                inputNodeCount,
                inputDirectedRelationshipCount,
                LouvainCommunityDetector.ALGORITHM_VERSION,
                parameters,
                SocialNetworkAnalysisCacheKeyFactory.ANALYSIS_SCOPE,
                identity == null ? null : identity.dataFingerprint(),
                identity == null ? List.of() : identity.inputFiles(),
                identity == null
                        ? SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION
                        : identity.inputContractVersion(),
                identity == null
                        ? SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION
                        : identity.relationProjectionVersion(),
                identity == null
                        ? LouvainAnalysisParameters.DEFAULT.relationProjection().resultName()
                        : identity.projection(),
                LouvainQualityCalculator.QUALITY_CONTRACT_VERSION,
                communities,
                membership,
                quality == null ? 0 : quality.unassignedNodeCount(),
                quality == null ? List.of() : quality.unassignedNodeIds(),
                workerCompleted ? result.detection().iterations() : 0,
                quality,
                result == null ? 0 : result.elapsedMillis(),
                result == null ? 0 : result.peakJobMemoryBytes(),
                result == null ? 0 : result.totalCpuTime100Nanoseconds(),
                result == null ? 0 : result.requestBytes(),
                result == null ? 0 : result.resultArtifactBytes(),
                identity == null ? null : identity.cacheKey().value(),
                cacheHit);
    }

    private Identity currentIdentity(
            String datasetId, String datasetVersion, String sessionId) {
        SocialNetworkDataset dataset = resolveDatasetContext(datasetId, datasetVersion, sessionId);
        try {
            SocialNetworkDataFingerprintService.Fingerprint fingerprint;
            if (dataset.isUploadSession()) {
                SocialNetworkWorkspaceSnapshot snapshot = workspace.get(
                        dataset.datasetId(), dataset.datasetVersion(), dataset.sessionId());
                fingerprint = snapshot.inputFingerprint();
                if (fingerprint == null
                        || !dataset.datasetId().equals(fingerprint.datasetId())
                        || !dataset.datasetVersion().equals(fingerprint.datasetVersion())
                        || !dataset.normalizationVersion()
                                .equals(fingerprint.normalizationVersion())) {
                    return null;
                }
            } else {
                requireSupportedBuiltin(dataset);
                fingerprint = fingerprintService.fingerprint(dataset);
            }
            return cacheKeyFactory.louvain(
                    dataset,
                    fingerprint,
                    LouvainAnalysisParameters.DEFAULT);
        } catch (IOException exception) {
            return null;
        }
    }

    private SocialNetworkDataset resolveDatasetContext(
            String datasetId, String datasetVersion, String sessionId) {
        if (datasetVersion == null && sessionId == null) {
            SocialNetworkDataset dataset = registry.findDataset(datasetId);
            if (dataset.isUploadSession()) {
                throw new SocialNetworkDatasetContextException();
            }
            return dataset;
        }
        if (datasetVersion == null || datasetVersion.isBlank()) {
            throw new SocialNetworkDatasetContextException();
        }
        return registry.resolveDatasetContext(datasetId, datasetVersion, sessionId);
    }

    private SocialNetworkGraphResult networkFor(Identity identity) {
        if (!isUploadIdentity(identity)) {
            return workspace.getNetworkForAnalysis(
                    identity.datasetId(), identity.dataFingerprint());
        }
        SocialNetworkDataset dataset = registry.resolveDatasetContext(
                identity.datasetId(), identity.datasetVersion(), identity.sessionId());
        SocialNetworkWorkspaceSnapshot snapshot = workspace.get(
                dataset.datasetId(), dataset.datasetVersion(), dataset.sessionId());
        if (!identity.dataFingerprint().equals(snapshot.inputFingerprint().dataFingerprint())) {
            throw new IllegalStateException("upload snapshot fingerprint changed");
        }
        return snapshot.network();
    }

    private boolean fingerprintMatches(Identity identity) {
        try {
            if (isUploadIdentity(identity)) {
                SocialNetworkDataset dataset = registry.resolveDatasetContext(
                        identity.datasetId(), identity.datasetVersion(), identity.sessionId());
                SocialNetworkWorkspaceSnapshot snapshot = workspace.get(
                        dataset.datasetId(), dataset.datasetVersion(), dataset.sessionId());
                return snapshot.inputFingerprint() != null
                        && identity.dataFingerprint().equals(
                                snapshot.inputFingerprint().dataFingerprint());
            }
            return identity.dataFingerprint().equals(
                    fingerprintService.fingerprint(
                                    registry.findDataset(identity.datasetId()))
                            .dataFingerprint());
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private Optional<SocialNetworkAnalysisResultCache.Hit<LouvainWorkflowCacheValue>> cached(
            Identity identity) {
        Optional<SocialNetworkAnalysisResultCache.Hit<LouvainWorkflowCacheValue>> cached =
                resultCache.find(
                CacheResultType.LOUVAIN_ANALYSIS,
                identity.cacheKey(),
                LouvainWorkflowCacheValue.class);
        if (cached.isPresent()) {
            return cached;
        }
        if (isUploadIdentity(identity)) {
            return Optional.empty();
        }
        Optional<LouvainAnalysisWorkflowView> persisted =
                resultStore.loadLouvain(identity.cacheKey().value());
        if (persisted.isEmpty()
                || !identity.dataFingerprint().equals(
                        persisted.get().dataFingerprint())) {
            return Optional.empty();
        }
        LouvainAnalysisWorkflowView view = persisted.get();
        if (!isReusableResult(view, identity)) {
            return Optional.empty();
        }
        LouvainAnalysisParameters parameters = LouvainAnalysisParameters.DEFAULT;
        CommunityDetectionResult detection = new CommunityDetectionResult(
                view.communities(),
                view.communityByNodeId(),
                view.unassignedNodeCount(),
                view.iterations(),
                true,
                view.algorithmVersion(),
                view.projection(),
                CommunityDetectionStatus.COMPLETED);
        LouvainWorkerRunResult restored = new LouvainWorkerRunResult(
                Status.COMPLETED,
                view.algorithmVersion(),
                parameters.resolution(),
                parameters,
                detection,
                view.quality(),
                null,
                view.elapsedMillis(),
                view.peakJobMemoryBytes(),
                view.totalCpuTime100Nanoseconds(),
                view.requestBytes(),
                view.resultArtifactBytes());
        LouvainWorkflowCacheValue value = new LouvainWorkflowCacheValue(
                restored,
                view.inputNodeCount(),
                view.inputDirectedRelationshipCount(),
                view.requestedAt(),
                view.finishedAt());
        resultCache.put(
                CacheResultType.LOUVAIN_ANALYSIS,
                identity.cacheKey(),
                identity.sessionId(),
                value,
                view.finishedAt());
        return resultCache.find(
                CacheResultType.LOUVAIN_ANALYSIS,
                identity.cacheKey(),
                LouvainWorkflowCacheValue.class);
    }

    private static boolean isReusableResult(
            LouvainAnalysisWorkflowView view, Identity identity) {
        if (view == null
                || !identity.datasetId().equals(view.datasetId())
                || !identity.datasetVersion().equals(view.datasetVersion())
                || !"COMPLETED".equals(view.state())
                || !"COMPLETED".equals(view.workerStatus())
                || !view.complete()
                || !view.workerCompleted()
                || !view.converged()
                || view.quality() == null
                || !SocialNetworkAnalysisCacheKeyFactory.ANALYSIS_SCOPE
                        .equals(view.analysisScope())
                || !identity.dataFingerprint().equals(view.dataFingerprint())
                || !identity.inputFiles().equals(view.inputFiles())
                || !identity.inputContractVersion().equals(view.inputContractVersion())
                || !identity.relationProjectionVersion()
                        .equals(view.relationProjectionVersion())
                || !identity.projection().equals(view.projection())
                || !identity.parameters().equals(view.algorithmParameters())
                || !identity.cacheKey().value().equals(view.cacheKey())
                || !LouvainCommunityDetector.ALGORITHM_VERSION
                        .equals(view.algorithmVersion())
                || !LouvainQualityCalculator.QUALITY_CONTRACT_VERSION
                        .equals(view.qualityContractVersion())) {
            return false;
        }

        var quality = view.quality();
        Set<String> unassigned = new HashSet<>(view.unassignedNodeIds());
        return quality.inputNodeCount() == view.inputNodeCount()
                && quality.assignedNodeCount() + quality.unassignedNodeCount()
                        == view.inputNodeCount()
                && quality.assignedNodeCount() == view.communityByNodeId().size()
                && quality.unassignedNodeCount() == view.unassignedNodeCount()
                && unassigned.size() == view.unassignedNodeIds().size()
                && unassigned.equals(new HashSet<>(quality.unassignedNodeIds()))
                && view.communities().stream()
                        .mapToInt(community -> community.nodeCount())
                        .sum() == quality.assignedNodeCount();
    }

    private void requireSupportedBuiltin(SocialNetworkDataset dataset) {
        if (!SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID.equals(dataset.datasetId())
                || dataset.type() != DatasetType.BUILTIN
                || !SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION
                        .equals(dataset.datasetVersion())) {
            throw new IllegalArgumentException("Louvain analysis supports built-in v1 only");
        }
    }

    private static boolean isUploadIdentity(Identity identity) {
        return identity.datasetType() == DatasetType.UPLOAD_SESSION;
    }

    private static Map<String, Object> defaultAlgorithmParameters() {
        LouvainAnalysisParameters parameters = LouvainAnalysisParameters.DEFAULT;
        return Map.ofEntries(
                Map.entry("algorithmVersion", LouvainCommunityDetector.ALGORITHM_VERSION),
                Map.entry(
                        "workerProtocolVersion",
                        LouvainAnalysisWorkerProtocol.SCHEMA_VERSION),
                Map.entry(
                        "qualityContractVersion",
                        LouvainQualityCalculator.QUALITY_CONTRACT_VERSION),
                Map.entry(
                        "completenessContract",
                        SocialNetworkAnalysisCacheKeyFactory.LOUVAIN_COMPLETENESS_CONTRACT),
                Map.entry("resolution", parameters.resolution()),
                Map.entry("nodeOrder", parameters.nodeOrder().name()),
                Map.entry("relationProjection", parameters.relationProjection().resultName()),
                Map.entry("weighting", "unweighted"),
                Map.entry("randomSeed", "not_applicable"),
                Map.entry("maxIterations", LouvainCommunityDetector.MAX_ITERATIONS),
                Map.entry("scoreEpsilon", LouvainCommunityDetector.SCORE_EPSILON));
    }

    private static String state(LouvainWorkerRunResult result) {
        if (result.status() != Status.COMPLETED) {
            return result.status().name();
        }
        return result.detection().converged() ? "COMPLETED" : "COMPLETED_UNCONVERGED";
    }

    private static boolean sameKey(Identity left, Identity right) {
        return left.cacheKey().value().equals(right.cacheKey().value());
    }

    private static LouvainWorkerRunResult failedResult(String detail) {
        return new LouvainWorkerRunResult(
                Status.FAILED, null, 0.0, null, null, null, detail, 0, 0, 0, 0, 0);
    }

    private static LouvainWorkerRunResult failedResult(
            String detail, LouvainWorkerRunResult previous) {
        return new LouvainWorkerRunResult(
                Status.FAILED,
                null,
                0.0,
                null,
                null,
                null,
                detail,
                previous.elapsedMillis(),
                previous.peakJobMemoryBytes(),
                previous.totalCpuTime100Nanoseconds(),
                previous.requestBytes(),
                previous.resultArtifactBytes());
    }

    private record LouvainWorkflowCacheValue(
            LouvainWorkerRunResult result,
            int inputNodeCount,
            int inputDirectedRelationshipCount,
            Instant requestedAt,
            Instant finishedAt) {
    }

    public record ReadOnlySnapshot(
            LouvainAnalysisWorkflowView status,
            Optional<LouvainAnalysisWorkflowView> reusableResult) {

        public ReadOnlySnapshot {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reusableResult, "reusableResult");
        }
    }

    private static final class ActiveRun {

        private final Identity identity;
        private final Instant requestedAt;
        private String state = "RUNNING";
        private Instant finishedAt;
        private int inputNodeCount;
        private int inputDirectedRelationshipCount;
        private LouvainWorkerRunResult result;

        private ActiveRun(Identity identity, Instant requestedAt) {
            this.identity = identity;
            this.requestedAt = requestedAt;
        }
    }
}
