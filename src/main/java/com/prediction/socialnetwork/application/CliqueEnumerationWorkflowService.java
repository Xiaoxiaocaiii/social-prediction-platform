package com.prediction.socialnetwork.application;

import com.prediction.socialnetwork.cache.CacheResultType;
import com.prediction.socialnetwork.cache.SocialNetworkAnalysisCacheKeyFactory;
import com.prediction.socialnetwork.cache.SocialNetworkAnalysisCacheKeyFactory.Identity;
import com.prediction.socialnetwork.cache.SocialNetworkAnalysisResultCache;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.isolation.WindowsResourcePreflight;
import com.prediction.socialnetwork.network.CliqueEnumerationLimits;
import com.prediction.socialnetwork.network.CliqueEnumerationResult;
import com.prediction.socialnetwork.network.CliqueEnumerationStatus;
import com.prediction.socialnetwork.network.CliqueEnumerationWorkerProtocol;
import com.prediction.socialnetwork.network.NetworkEdge;
import com.prediction.socialnetwork.network.NetworkNode;
import com.prediction.socialnetwork.network.SocialNetworkGraphResult;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CliqueEnumerationWorkflowService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SocialNetworkDatasetRegistry registry;
    private final SocialNetworkWorkspaceService workspace;
    private final CliqueEnumerationExecutor runner;
    private final SocialNetworkDataFingerprintService fingerprintService;
    private final SocialNetworkAnalysisCacheKeyFactory cacheKeyFactory;
    private final SocialNetworkAnalysisResultCache resultCache;
    private final SocialNetworkAnalysisResultStore resultStore;
    private final Runnable resourcePreflight;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "social-network-clique-workflow");
        thread.setDaemon(true);
        return thread;
    });
    private final Object stateLock = new Object();

    private ActiveRun activeRun;

    @Autowired
    public CliqueEnumerationWorkflowService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            CliqueEnumerationExecutor runner,
            SocialNetworkDataFingerprintService fingerprintService,
            SocialNetworkAnalysisCacheKeyFactory cacheKeyFactory,
            SocialNetworkAnalysisResultCache resultCache,
            SocialNetworkAnalysisResultStore resultStore) {
        this(
                registry,
                workspace,
                runner,
                fingerprintService,
                cacheKeyFactory,
                resultCache,
                WindowsResourcePreflight::requireMinimumAvailableMemory,
                resultStore);
    }

    CliqueEnumerationWorkflowService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            CliqueEnumerationExecutor runner,
            SocialNetworkDataFingerprintService fingerprintService,
            SocialNetworkAnalysisCacheKeyFactory cacheKeyFactory,
            SocialNetworkAnalysisResultCache resultCache,
            Runnable resourcePreflight) {
        this(
                registry,
                workspace,
                runner,
                fingerprintService,
                cacheKeyFactory,
                resultCache,
                resourcePreflight,
                SocialNetworkAnalysisResultStore.disabled());
    }

    CliqueEnumerationWorkflowService(
            SocialNetworkDatasetRegistry registry,
            SocialNetworkWorkspaceService workspace,
            CliqueEnumerationExecutor runner,
            SocialNetworkDataFingerprintService fingerprintService,
            SocialNetworkAnalysisCacheKeyFactory cacheKeyFactory,
            SocialNetworkAnalysisResultCache resultCache,
            Runnable resourcePreflight,
            SocialNetworkAnalysisResultStore resultStore) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.fingerprintService = Objects.requireNonNull(
                fingerprintService, "fingerprintService");
        this.cacheKeyFactory = Objects.requireNonNull(cacheKeyFactory, "cacheKeyFactory");
        this.resultCache = Objects.requireNonNull(resultCache, "resultCache");
        this.resultStore = Objects.requireNonNull(resultStore, "resultStore");
        this.resourcePreflight = Objects.requireNonNull(resourcePreflight, "resourcePreflight");
    }

    public CliqueEnumerationWorkflowView request(String datasetId) {
        return request(datasetId, false);
    }

    public CliqueEnumerationWorkflowView request(String datasetId, boolean force) {
        Identity identity = currentIdentity(datasetId);
        if (identity == null) {
            return fingerprintUnavailableView(datasetId);
        }
        if (!force) {
            synchronized (stateLock) {
                if (activeRun != null
                        && "RUNNING".equals(activeRun.state)
                        && sameKey(activeRun.identity, identity)) {
                    return view(activeRun, 0, 20);
                }
            }
            Optional<SocialNetworkAnalysisResultCache.Hit<CliqueWorkflowCacheValue>> cached =
                    cached(identity);
            if (cached.isPresent()) {
                return view(identity, cached.get().result(), true, 0, 20);
            }
        } else {
            cached(identity);
        }
        synchronized (stateLock) {
            if (activeRun != null && "RUNNING".equals(activeRun.state)) {
                if (sameKey(activeRun.identity, identity)) {
                    return view(activeRun, 0, 20);
                }
                return idleView(identity, "BUSY", "analysis_already_running", 0, 20);
            }
            ActiveRun run = new ActiveRun(identity, Instant.now());
            activeRun = run;
            try {
                executor.execute(() -> calculate(datasetId, run));
            } catch (RejectedExecutionException exception) {
                finish(run, failedResult("workflow_executor_unavailable"), false);
            }
            return view(run, 0, 20);
        }
    }

    public CliqueEnumerationWorkflowView status(
            String datasetId, int requestedOffset, int requestedPageSize) {
        int offset = Math.max(0, requestedOffset);
        int pageSize = Math.max(1, Math.min(requestedPageSize, MAX_PAGE_SIZE));
        Identity identity = currentIdentity(datasetId);
        if (identity == null) {
            return fingerprintUnavailableView(datasetId);
        }
        synchronized (stateLock) {
            if (activeRun != null
                    && "RUNNING".equals(activeRun.state)
                    && sameKey(activeRun.identity, identity)) {
                return view(activeRun, offset, pageSize);
            }
        }
        Optional<SocialNetworkAnalysisResultCache.Hit<CliqueWorkflowCacheValue>> cached =
                cached(identity);
        if (cached.isPresent()) {
            return view(identity, cached.get().result(), true, offset, pageSize);
        }
        synchronized (stateLock) {
            if (activeRun != null && sameKey(activeRun.identity, identity)) {
                return view(activeRun, offset, pageSize);
            }
            return idleView(identity, "NOT_REQUESTED", null, offset, pageSize);
        }
    }

    Optional<CliqueEnumerationResult> completedMemberships(String datasetId) {
        Identity identity = currentIdentity(datasetId);
        if (identity == null) {
            return Optional.empty();
        }
        return cached(identity)
                .map(hit -> hit.result().result())
                .filter(result -> result.isComplete() && result.membershipListComplete());
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private void calculate(String datasetId, ActiveRun run) {
        try {
            resourcePreflight.run();
        } catch (IllegalStateException exception) {
            if (isPhysicalMemoryRefusal(exception)) {
                finish(
                        run,
                        CliqueEnumerationResult.externalTermination(
                                CliqueEnumerationStatus.MEMORY_LIMIT,
                                CliqueEnumerationWorkerProtocol.DEFAULT_WORKER_LIMITS,
                                "physical_memory_preflight"),
                        false);
                return;
            }
            finish(run, failedResult("workflow_input_or_runner_failure"), true);
            return;
        } catch (RuntimeException exception) {
            finish(run, failedResult("workflow_input_or_runner_failure"), true);
            return;
        }

        try {
            SocialNetworkGraphResult network = workspace.getNetworkForAnalysis(
                    datasetId, run.identity.dataFingerprint());
            if (!fingerprintMatches(datasetId, run.identity.dataFingerprint())) {
                finish(run, failedResult("input_changed_during_snapshot_load"), false);
                return;
            }
            List<NetworkNode> nodes = network.nodes();
            List<NetworkEdge> directedEdges = network.edges();
            synchronized (stateLock) {
                run.inputNodeCount = nodes.size();
                run.inputDirectedEdgeCount = directedEdges.size();
            }
            CliqueEnumerationResult completed = runner.run(
                    nodes, directedEdges, Thread.currentThread()::isInterrupted);
            if (!fingerprintMatches(datasetId, run.identity.dataFingerprint())) {
                finish(run, failedResult("input_changed_during_analysis"), false);
                return;
            }
            finish(run, completed, completed.status() != CliqueEnumerationStatus.BUSY);
        } catch (RuntimeException exception) {
            finish(run, failedResult("workflow_input_or_runner_failure"), true);
        }
    }

    private static boolean isPhysicalMemoryRefusal(IllegalStateException exception) {
        return exception.getMessage() != null
                && exception.getMessage().startsWith(
                        "Analysis refused: available physical memory");
    }

    private void finish(
            ActiveRun run, CliqueEnumerationResult completed, boolean cacheable) {
        synchronized (stateLock) {
            run.result = Objects.requireNonNull(completed, "completed");
            run.state = completed.status().name();
            run.finishedAt = Instant.now();
            boolean previousComplete = resultCache.find(
                            CacheResultType.CLIQUE_ANALYSIS,
                            run.identity.cacheKey(),
                            CliqueWorkflowCacheValue.class)
                    .map(hit -> hit.result().result().isComplete()
                            && hit.result().result().membershipListComplete())
                    .orElse(false);
            boolean currentComplete = completed.isComplete()
                    && completed.membershipListComplete();
            if (cacheable && (!previousComplete || currentComplete)) {
                resultCache.put(
                        CacheResultType.CLIQUE_ANALYSIS,
                        run.identity.cacheKey(),
                        new CliqueWorkflowCacheValue(
                                completed,
                                run.inputNodeCount,
                                run.inputDirectedEdgeCount,
                                run.requestedAt,
                                run.finishedAt),
                        run.finishedAt);
            }
        }
        resultStore.saveClique(buildView(
                run.identity,
                run.state,
                completed,
                run.requestedAt,
                run.finishedAt,
                run.inputNodeCount,
                run.inputDirectedEdgeCount,
                false,
                0,
                Math.max(1, completed.retainedCliqueCount()),
                completed.terminationDetail()));
    }

    private CliqueEnumerationWorkflowView view(ActiveRun run, int offset, int pageSize) {
        return buildView(
                run.identity,
                run.state,
                run.result,
                run.requestedAt,
                run.finishedAt,
                run.inputNodeCount,
                run.inputDirectedEdgeCount,
                false,
                offset,
                pageSize,
                run.result == null ? null : run.result.terminationDetail());
    }

    private static CliqueEnumerationWorkflowView view(
            Identity identity,
            CliqueWorkflowCacheValue cached,
            boolean cacheHit,
            int offset,
            int pageSize) {
        return buildView(
                identity,
                cached.result().status().name(),
                cached.result(),
                cached.requestedAt(),
                cached.finishedAt(),
                cached.inputNodeCount(),
                cached.inputDirectedEdgeCount(),
                cacheHit,
                offset,
                pageSize,
                cached.result().terminationDetail());
    }

    private static CliqueEnumerationWorkflowView idleView(
            Identity identity,
            String state,
            String detail,
            int offset,
            int pageSize) {
        return buildView(
                identity, state, null, null, null, 0, 0, false, offset, pageSize, detail);
    }

    private static CliqueEnumerationWorkflowView buildView(
            Identity identity,
            String state,
            CliqueEnumerationResult current,
            Instant requestedAt,
            Instant finishedAt,
            int inputNodeCount,
            int inputDirectedEdgeCount,
            boolean cacheHit,
            int offset,
            int pageSize,
            String detail) {
        CliqueEnumerationLimits limits = current == null
                ? CliqueEnumerationWorkerProtocol.DEFAULT_WORKER_LIMITS
                : current.limits();
        return new CliqueEnumerationWorkflowView(
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID,
                SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION,
                state,
                current != null && current.isComplete(),
                detail,
                requestedAt,
                finishedAt,
                inputNodeCount,
                inputDirectedEdgeCount,
                current == null ? 0 : current.retainedCliqueCount(),
                current == null ? 0 : current.enumeratedCliqueCount(),
                current == null ? 0 : current.largestObservedCliqueSize(),
                current == null ? Map.of() : current.cliqueSizeDistribution(),
                current == null ? Map.of() : current.nodeParticipationCounts(),
                current != null && current.membershipListComplete(),
                offset,
                pageSize,
                current == null ? List.of() : current.page(offset, pageSize),
                limits.timeLimit().toMillis(),
                limits.maxCliques(),
                limits.maxCliqueArrayBytes(),
                "data_fingerprint",
                true,
                identity.projection(),
                SocialNetworkAnalysisCacheKeyFactory.ANALYSIS_SCOPE,
                identity.dataFingerprint(),
                identity.inputFiles(),
                identity.inputContractVersion(),
                identity.relationProjectionVersion(),
                String.valueOf(identity.parameters().get("algorithmVersion")),
                identity.parameters(),
                identity.cacheKey().value(),
                cacheHit);
    }

    private Identity currentIdentity(String datasetId) {
        requireSupportedDataset(datasetId);
        SocialNetworkDataset dataset = registry.findDataset(datasetId);
        try {
            return cacheKeyFactory.clique(
                    dataset, fingerprintService.fingerprint(dataset));
        } catch (IOException exception) {
            return null;
        }
    }

    private boolean fingerprintMatches(String datasetId, String expectedFingerprint) {
        try {
            return expectedFingerprint.equals(
                    fingerprintService.fingerprint(registry.findDataset(datasetId))
                            .dataFingerprint());
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private Optional<SocialNetworkAnalysisResultCache.Hit<CliqueWorkflowCacheValue>> cached(
            Identity identity) {
        Optional<SocialNetworkAnalysisResultCache.Hit<CliqueWorkflowCacheValue>> cached =
                resultCache.find(
                CacheResultType.CLIQUE_ANALYSIS,
                identity.cacheKey(),
                CliqueWorkflowCacheValue.class);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<CliqueEnumerationWorkflowView> persisted =
                resultStore.loadClique(identity.cacheKey().value());
        if (persisted.isEmpty()
                || !identity.dataFingerprint().equals(
                        persisted.get().dataFingerprint())) {
            return Optional.empty();
        }
        CliqueEnumerationWorkflowView view = persisted.get();
        CliqueEnumerationResult restored = new CliqueEnumerationResult(
                view.cliques(),
                CliqueEnumerationStatus.COMPLETED,
                CliqueEnumerationWorkerProtocol.DEFAULT_WORKER_LIMITS,
                2,
                null,
                view.enumeratedCliqueCount(),
                view.cliqueSizeDistribution(),
                view.nodeParticipationCounts(),
                true);
        CliqueWorkflowCacheValue value = new CliqueWorkflowCacheValue(
                restored,
                view.inputNodeCount(),
                view.inputDirectedEdgeCount(),
                view.requestedAt(),
                view.finishedAt());
        resultCache.put(
                CacheResultType.CLIQUE_ANALYSIS,
                identity.cacheKey(),
                value,
                view.finishedAt());
        return resultCache.find(
                CacheResultType.CLIQUE_ANALYSIS,
                identity.cacheKey(),
                CliqueWorkflowCacheValue.class);
    }

    private static boolean sameKey(Identity left, Identity right) {
        return left.cacheKey().value().equals(right.cacheKey().value());
    }

    private void requireSupportedDataset(String datasetId) {
        SocialNetworkDataset dataset = registry.findDataset(datasetId);
        if (!SocialNetworkDatasetRegistry.BUILTIN_DATASET_ID.equals(datasetId)
                || dataset.type() != DatasetType.BUILTIN
                || !SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION
                        .equals(dataset.datasetVersion())) {
            throw new IllegalArgumentException("Clique analysis supports built-in v1 only");
        }
    }

    private CliqueEnumerationWorkflowView fingerprintUnavailableView(String datasetId) {
        SocialNetworkDataset dataset = registry.findDataset(datasetId);
        CliqueEnumerationLimits limits = CliqueEnumerationWorkerProtocol.DEFAULT_WORKER_LIMITS;
        return new CliqueEnumerationWorkflowView(
                dataset.datasetId(),
                dataset.datasetVersion(),
                "FAILED",
                false,
                "input_fingerprint_unavailable",
                null,
                Instant.now(),
                0,
                0,
                0,
                0,
                0,
                Map.of(),
                Map.of(),
                false,
                0,
                20,
                List.of(),
                limits.timeLimit().toMillis(),
                limits.maxCliques(),
                limits.maxCliqueArrayBytes(),
                "unavailable",
                false,
                SocialNetworkAnalysisCacheKeyFactory.PROJECTION,
                SocialNetworkAnalysisCacheKeyFactory.ANALYSIS_SCOPE,
                null,
                List.of(),
                SocialNetworkDataFingerprintService.INPUT_CONTRACT_VERSION,
                SocialNetworkDataFingerprintService.RELATION_PROJECTION_VERSION,
                CliqueEnumerationWorkerProtocol.ALGORITHM_VERSION,
                Map.of(
                        "minimumCliqueSize", 3,
                        "workerProtocolVersion", CliqueEnumerationWorkerProtocol.SCHEMA_VERSION),
                null,
                false);
    }

    private static CliqueEnumerationResult failedResult(String detail) {
        return CliqueEnumerationResult.externalTermination(
                CliqueEnumerationStatus.FAILED,
                CliqueEnumerationWorkerProtocol.DEFAULT_WORKER_LIMITS,
                detail);
    }

    private record CliqueWorkflowCacheValue(
            CliqueEnumerationResult result,
            int inputNodeCount,
            int inputDirectedEdgeCount,
            Instant requestedAt,
            Instant finishedAt) {
    }

    private static final class ActiveRun {

        private final Identity identity;
        private final Instant requestedAt;
        private String state = "RUNNING";
        private Instant finishedAt;
        private int inputNodeCount;
        private int inputDirectedEdgeCount;
        private CliqueEnumerationResult result;

        private ActiveRun(Identity identity, Instant requestedAt) {
            this.identity = identity;
            this.requestedAt = requestedAt;
        }
    }
}
