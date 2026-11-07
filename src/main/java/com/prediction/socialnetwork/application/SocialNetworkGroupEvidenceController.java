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
            @RequestParam(defaultValue = "members") String section,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int pageSize) {
        return success(evidenceService.clique(
                datasetId, cliqueIndex, section, offset, pageSize));
    }

    @GetMapping("/louvain/communities/{communityId}/evidence")
    public SocialNetworkApiResponse<SocialNetworkGroupEvidenceView> louvainEvidence(
            @PathVariable String datasetId,
            @PathVariable String communityId,
            @RequestParam(defaultValue = "members") String section,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int pageSize) {
        return success(evidenceService.louvain(
                datasetId, communityId, section, offset, pageSize));
    }

    @GetMapping("/louvain/communities/{first}/between/{second}/evidence")
    public SocialNetworkApiResponse<SocialNetworkGroupEvidenceView> betweenCommunities(
            @PathVariable String datasetId,
            @PathVariable String first,
            @PathVariable String second,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int pageSize) {
        return success(evidenceService.betweenCommunities(
                datasetId, first, second, offset, pageSize));
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
            SocialNetworkGroupEvidenceView view) {
        Map<String, Object> meta = Map.of(
                "datasetId", view.datasetId(),
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
