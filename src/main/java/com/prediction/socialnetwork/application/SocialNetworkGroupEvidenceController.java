package com.prediction.socialnetwork.application;

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
@RequestMapping("/api/social-network/datasets/{datasetId}/network")
public class SocialNetworkGroupEvidenceController {

    private final SocialNetworkGroupEvidenceService evidenceService;

    public SocialNetworkGroupEvidenceController(
            SocialNetworkGroupEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping("/cliques/{cliqueIndex}/evidence")
    public SocialNetworkApiResponse<SocialNetworkGroupEvidenceView> cliqueEvidence(
            @PathVariable String datasetId,
            @PathVariable int cliqueIndex,
            @RequestParam(required = false) String datasetVersion,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "members") String section,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int pageSize) {
        SocialNetworkGroupEvidenceView view =
                datasetVersion == null && sessionId == null
                        ? evidenceService.clique(
                                datasetId, cliqueIndex, section, offset, pageSize)
                        : evidenceService.clique(
                                datasetId, datasetVersion, sessionId,
                                cliqueIndex, section, offset, pageSize);
        return success(
                view,
                datasetVersion == null
                        ? SocialNetworkDatasetRegistry.BUILTIN_DATASET_VERSION
                        : datasetVersion,
                sessionId);
    }

    @GetMapping("/louvain/communities/{communityId}/evidence")
    public SocialNetworkApiResponse<SocialNetworkGroupEvidenceView> louvainEvidence(
            @PathVariable String datasetId,
            @PathVariable String communityId,
            @RequestParam(required = false) String datasetVersion,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "members") String section,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int pageSize) {
        return success(evidenceService.louvain(
                datasetId, datasetVersion, sessionId,
                communityId, section, offset, pageSize),
                datasetVersion,
                sessionId);
    }

    @GetMapping("/louvain/communities/{first}/between/{second}/evidence")
    public SocialNetworkApiResponse<SocialNetworkGroupEvidenceView> betweenCommunities(
            @PathVariable String datasetId,
            @PathVariable String first,
            @PathVariable String second,
            @RequestParam(required = false) String datasetVersion,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int pageSize) {
        return success(evidenceService.betweenCommunities(
                datasetId, datasetVersion, sessionId, first, second, offset, pageSize),
                datasetVersion,
                sessionId);
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
                "SN_GROUP_EVIDENCE_BAD_REQUEST",
                "证据查询参数无效或当前数据集不受支持");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> unavailable(
            IllegalStateException exception) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SN_GROUP_EVIDENCE_UNAVAILABLE",
                "当前分析证据暂不可用");
    }

    private static SocialNetworkApiResponse<SocialNetworkGroupEvidenceView> success(
            SocialNetworkGroupEvidenceView view,
            String datasetVersion,
            String sessionId) {
        Map<String, Object> meta = Map.of(
                "datasetId", view.datasetId(),
                "datasetVersion",
                datasetVersion == null ? "" : datasetVersion,
                "sessionId", sessionId == null ? "" : sessionId,
                "analysisType", view.analysisType(),
                "dataFingerprint",
                view.dataFingerprint() == null ? "" : view.dataFingerprint(),
                "targetId", view.targetId());
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(), view, List.of(), meta);
    }

    private static ResponseEntity<SocialNetworkApiResponse<Void>> error(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(
                SocialNetworkApiResponse.error(
                        UUID.randomUUID().toString(),
                        code,
                        message,
                        Map.of("scope", Map.of())));
    }
}
