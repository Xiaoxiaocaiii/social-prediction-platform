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
public class SocialNetworkGroupInsightsController {

    private final SocialNetworkGroupInsightsService service;

    public SocialNetworkGroupInsightsController(SocialNetworkGroupInsightsService service) {
        this.service = service;
    }

    @GetMapping("/cliques/insights")
    public SocialNetworkApiResponse<SocialNetworkGroupInsightsService.CliqueView> clique(
            @PathVariable String datasetId,
            @RequestParam(required = false) String datasetVersion,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int pageSize) {
        SocialNetworkGroupInsightsService.CliqueView view =
                datasetVersion == null && sessionId == null
                        ? service.clique(datasetId, offset, pageSize)
                        : service.clique(
                                datasetId, datasetVersion, sessionId, offset, pageSize);
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(), view,
                List.of(), contextMeta(datasetId, datasetVersion, sessionId));
    }

    @GetMapping("/louvain/insights")
    public SocialNetworkApiResponse<SocialNetworkGroupInsightsService.LouvainView> louvain(
            @PathVariable String datasetId,
            @RequestParam(required = false) String datasetVersion,
            @RequestParam(required = false) String sessionId) {
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(),
                service.louvain(datasetId, datasetVersion, sessionId),
                List.of(), Map.of(
                        "datasetId", datasetId,
                        "datasetVersion", datasetVersion == null ? "" : datasetVersion,
                        "sessionId", sessionId == null ? "" : sessionId));
    }

    @GetMapping("/cliques/insights/shared")
    public SocialNetworkApiResponse<SocialNetworkGroupInsightsService.SharedView> shared(
            @PathVariable String datasetId,
            @RequestParam(required = false) String datasetVersion,
            @RequestParam(required = false) String sessionId,
            @RequestParam String first,
            @RequestParam String second,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int pageSize) {
        SocialNetworkGroupInsightsService.SharedView view =
                datasetVersion == null && sessionId == null
                        ? service.sharedCliques(
                                datasetId, first, second, offset, pageSize)
                        : service.sharedCliques(
                                datasetId, datasetVersion, sessionId,
                                first, second, offset, pageSize);
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(),
                view,
                List.of(), contextMeta(datasetId, datasetVersion, sessionId));
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
                "SN_GROUP_INSIGHTS_BAD_REQUEST",
                "结论查询参数无效或当前数据集不受支持");
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

    private static Map<String, Object> contextMeta(
            String datasetId, String datasetVersion, String sessionId) {
        return Map.of(
                "datasetId", datasetId,
                "datasetVersion", datasetVersion == null ? "" : datasetVersion,
                "sessionId", sessionId == null ? "" : sessionId);
    }
}
