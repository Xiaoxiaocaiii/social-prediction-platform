package com.prediction.socialnetwork.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social-network/datasets/{datasetId}/network/bridge-candidates")
public class SocialNetworkBridgeCandidateController {

    private final SocialNetworkBridgeCandidateService candidateService;

    public SocialNetworkBridgeCandidateController(
            SocialNetworkBridgeCandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping
    public SocialNetworkApiResponse<SocialNetworkBridgeCandidateService.CandidateListView>
            candidates(
                    @PathVariable String datasetId,
                    @RequestParam(required = false) String datasetVersion,
                    @RequestParam(required = false) String sessionId,
                    @RequestParam(defaultValue = "0") int offset,
                    @RequestParam(defaultValue = "20") int pageSize) {
        SocialNetworkBridgeCandidateService.CandidateListView view =
                datasetVersion == null && sessionId == null
                        ? candidateService.candidates(datasetId, offset, pageSize)
                        : candidateService.candidates(
                                datasetId, datasetVersion, sessionId, offset, pageSize);
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(),
                view,
                List.of(),
                metadata(
                        view.datasetId(),
                        datasetVersion,
                        sessionId,
                        view.scope(),
                        view.analysisIdentity() == null
                                ? ""
                                : view.analysisIdentity().analysisResultId()));
    }

    @GetMapping("/{accountId}/evidence")
    public SocialNetworkApiResponse<SocialNetworkBridgeCandidateService.EvidenceView>
            evidence(
                    @PathVariable String datasetId,
                    @PathVariable String accountId,
                    @RequestParam(required = false) String datasetVersion,
                    @RequestParam(required = false) String sessionId,
                    @RequestParam(required = false) String communityId,
                    @RequestParam(defaultValue = "all") String direction,
                    @RequestParam(defaultValue = "") String expectedAnalysisResultId,
                    @RequestParam(defaultValue = "0") int offset,
                    @RequestParam(defaultValue = "20") int pageSize) {
        SocialNetworkBridgeCandidateService.EvidenceView view =
                datasetVersion == null && sessionId == null
                        ? candidateService.evidence(
                                datasetId,
                                accountId,
                                communityId,
                                direction,
                                expectedAnalysisResultId,
                                offset,
                                pageSize)
                        : candidateService.evidence(
                                datasetId,
                                datasetVersion,
                                sessionId,
                                accountId,
                                communityId,
                                direction,
                                expectedAnalysisResultId,
                                offset,
                                pageSize);
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(),
                view,
                List.of(),
                metadata(
                        view.datasetId(),
                        datasetVersion,
                        sessionId,
                        view.scope(),
                        view.analysisIdentity() == null
                                ? ""
                                : view.analysisIdentity().analysisResultId(),
                        view.accountId()));
    }

    @ExceptionHandler(SocialNetworkDatasetSessionExpiredException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> expiredSession(
            SocialNetworkDatasetSessionExpiredException exception) {
        return error(
                HttpStatus.GONE,
                "SN_DATASET_SESSION_EXPIRED",
                "上传数据会话已失效，请重新上传数据");
    }

    @ExceptionHandler(SocialNetworkDatasetContextException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> invalidContext(
            SocialNetworkDatasetContextException exception) {
        return error(
                HttpStatus.BAD_REQUEST,
                "SN_DATASET_CONTEXT_INVALID",
                "数据集版本或上传会话与当前请求不匹配");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> invalidRequest(
            IllegalArgumentException exception) {
        return error(
                HttpStatus.BAD_REQUEST,
                "SN_BRIDGE_CANDIDATE_BAD_REQUEST",
                "跨社群候选查询参数无效或当前数据集不受支持");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> unavailable(
            IllegalStateException exception) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SN_BRIDGE_CANDIDATE_UNAVAILABLE",
                "当前跨社群候选证据暂不可用");
    }

    private static ResponseEntity<SocialNetworkApiResponse<Void>> error(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(
                SocialNetworkApiResponse.error(
                        UUID.randomUUID().toString(),
                        code,
                        message,
                        Map.of("scope", "full_normalized_network")));
    }

    private static Map<String, Object> metadata(
            String datasetId,
            String datasetVersion,
            String sessionId,
            String scope,
            String analysisResultId) {
        return metadata(
                datasetId, datasetVersion, sessionId, scope, analysisResultId, null);
    }

    private static Map<String, Object> metadata(
            String datasetId,
            String datasetVersion,
            String sessionId,
            String scope,
            String analysisResultId,
            String accountId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("datasetId", datasetId);
        metadata.put("datasetVersion", datasetVersion == null ? "" : datasetVersion);
        metadata.put("sessionId", sessionId == null ? "" : sessionId);
        metadata.put("scope", scope);
        metadata.put("analysisResultId", analysisResultId);
        if (accountId != null) {
            metadata.put("accountId", accountId);
        }
        return metadata;
    }
}
