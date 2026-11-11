package com.prediction.socialnetwork.cache;

import com.prediction.socialnetwork.application.SocialNetworkDataFingerprintService;
import com.prediction.socialnetwork.domain.DatasetType;
import com.prediction.socialnetwork.domain.SocialNetworkDataset;
import com.prediction.socialnetwork.network.CliqueEnumerationWorkerProtocol;
import com.prediction.socialnetwork.network.LouvainAnalysisParameters;
import com.prediction.socialnetwork.network.LouvainAnalysisWorkerProtocol;
import com.prediction.socialnetwork.network.LouvainCommunityDetector;
import com.prediction.socialnetwork.network.LouvainQualityCalculator;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class SocialNetworkAnalysisCacheKeyFactory {

    public static final String ANALYSIS_SCOPE = "full_normalized_network";
    public static final String PROJECTION = "undirected_simple_any_direction";
    public static final String CLIQUE_COMPLETENESS_CONTRACT = "clique-completeness-v2";
    public static final String LOUVAIN_COMPLETENESS_CONTRACT = "louvain-completeness-v1";

    private final SocialNetworkCacheKeyGenerator keyGenerator =
            new SocialNetworkCacheKeyGenerator();

    public Identity clique(
            SocialNetworkDataset dataset,
            SocialNetworkDataFingerprintService.Fingerprint fingerprint) {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(fingerprint, "fingerprint");
        var limits = CliqueEnumerationWorkerProtocol.DEFAULT_WORKER_LIMITS;
        Map<String, Object> parameters = Map.of(
                "algorithmVersion", CliqueEnumerationWorkerProtocol.ALGORITHM_VERSION,
                "workerProtocolVersion", CliqueEnumerationWorkerProtocol.SCHEMA_VERSION,
                "minimumCliqueSize", 3,
                "timeLimitMillis", limits.timeLimit().toMillis(),
                "maxCliques", limits.maxCliques(),
                "membershipArrayBytes", limits.maxCliqueArrayBytes(),
                "resultArtifactBytes",
                        CliqueEnumerationWorkerProtocol.MAX_RESULT_ARTIFACT_BYTES,
                "completenessContract", CLIQUE_COMPLETENESS_CONTRACT);
        return identity(
                dataset,
                fingerprint,
                CacheResultType.CLIQUE_ANALYSIS,
                PROJECTION,
                parameters);
    }

    public Identity louvain(
            SocialNetworkDataset dataset,
            SocialNetworkDataFingerprintService.Fingerprint fingerprint,
            LouvainAnalysisParameters parameters) {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(parameters, "parameters");
        Map<String, Object> algorithmParameters = Map.ofEntries(
                Map.entry("algorithmVersion", LouvainCommunityDetector.ALGORITHM_VERSION),
                Map.entry(
                        "workerProtocolVersion",
                        LouvainAnalysisWorkerProtocol.SCHEMA_VERSION),
                Map.entry(
                        "qualityContractVersion",
                        LouvainQualityCalculator.QUALITY_CONTRACT_VERSION),
                Map.entry("completenessContract", LOUVAIN_COMPLETENESS_CONTRACT),
                Map.entry("resolution", parameters.resolution()),
                Map.entry("nodeOrder", parameters.nodeOrder().name()),
                Map.entry(
                        "relationProjection",
                        parameters.relationProjection().resultName()),
                Map.entry("weighting", "unweighted"),
                Map.entry("randomSeed", "not_applicable"),
                Map.entry("maxIterations", LouvainCommunityDetector.MAX_ITERATIONS),
                Map.entry("scoreEpsilon", LouvainCommunityDetector.SCORE_EPSILON));
        return identity(
                dataset,
                fingerprint,
                CacheResultType.LOUVAIN_ANALYSIS,
                parameters.relationProjection().resultName(),
                algorithmParameters);
    }

    private Identity identity(
            SocialNetworkDataset dataset,
            SocialNetworkDataFingerprintService.Fingerprint fingerprint,
            CacheResultType resultType,
            String projection,
            Map<String, Object> parameters) {
        if (!dataset.datasetId().equals(fingerprint.datasetId())
                || !dataset.datasetVersion().equals(fingerprint.datasetVersion())
                || !dataset.normalizationVersion()
                        .equals(fingerprint.normalizationVersion())) {
            throw new IllegalArgumentException(
                    "Fingerprint identity does not match the selected dataset");
        }
        Map<String, Object> scope = Map.of(
                "analysisScope", ANALYSIS_SCOPE,
                "dataFingerprint", fingerprint.dataFingerprint(),
                "inputContractVersion", fingerprint.inputContractVersion(),
                "relationProjectionVersion", fingerprint.relationProjectionVersion(),
                "projection", projection);
        SocialNetworkVersionContext versions = SocialNetworkVersionContext.from(dataset);
        SocialNetworkCacheContext context = dataset.isUploadSession()
                ? SocialNetworkCacheContext.uploadSession(
                        dataset.datasetId(),
                        dataset.sessionId(),
                        versions,
                        resultType,
                        scope,
                        parameters)
                : SocialNetworkCacheContext.builtin(
                        dataset.datasetId(),
                        versions,
                        resultType,
                        scope,
                        parameters);
        return new Identity(
                resultType,
                keyGenerator.generate(context),
                fingerprint.dataFingerprint(),
                projection,
                parameters,
                fingerprint.files(),
                fingerprint.inputContractVersion(),
                fingerprint.relationProjectionVersion(),
                dataset.datasetId(),
                dataset.type(),
                dataset.sessionId(),
                dataset.datasetVersion());
    }

    public record Identity(
            CacheResultType resultType,
            SocialNetworkCacheKey cacheKey,
            String dataFingerprint,
            String projection,
            Map<String, Object> parameters,
            java.util.List<SocialNetworkDataFingerprintService.FileFingerprint> inputFiles,
            String inputContractVersion,
            String relationProjectionVersion,
            String datasetId,
            DatasetType datasetType,
            String sessionId,
            String datasetVersion) {

        public Identity {
            resultType = Objects.requireNonNull(resultType, "resultType");
            cacheKey = Objects.requireNonNull(cacheKey, "cacheKey");
            if (dataFingerprint == null || dataFingerprint.isBlank()) {
                throw new IllegalArgumentException("dataFingerprint must not be blank");
            }
            if (projection == null || projection.isBlank()) {
                throw new IllegalArgumentException("projection must not be blank");
            }
            parameters = Map.copyOf(parameters);
            inputFiles = java.util.List.copyOf(inputFiles);
            inputContractVersion = Objects.requireNonNull(
                    inputContractVersion, "inputContractVersion");
            relationProjectionVersion = Objects.requireNonNull(
                    relationProjectionVersion, "relationProjectionVersion");
            if (datasetId == null || datasetId.isBlank()
                    || datasetVersion == null || datasetVersion.isBlank()) {
                throw new IllegalArgumentException(
                        "dataset identity fields must not be blank");
            }
            datasetType = Objects.requireNonNull(datasetType, "datasetType");
            if (datasetType == DatasetType.BUILTIN && sessionId != null) {
                throw new IllegalArgumentException(
                        "builtin analysis identity must not have a sessionId");
            }
            if (datasetType == DatasetType.UPLOAD_SESSION
                    && (sessionId == null || sessionId.isBlank())) {
                throw new IllegalArgumentException(
                        "upload analysis identity must have a sessionId");
            }
        }
    }
}
