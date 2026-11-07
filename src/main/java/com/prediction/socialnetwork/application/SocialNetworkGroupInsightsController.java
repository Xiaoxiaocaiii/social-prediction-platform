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
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int pageSize) {
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(), service.clique(datasetId, offset, pageSize),
                List.of(), Map.of("datasetId", datasetId));
    }

    @GetMapping("/louvain/insights")
    public SocialNetworkApiResponse<SocialNetworkGroupInsightsService.LouvainView> louvain(
            @PathVariable String datasetId) {
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(), service.louvain(datasetId),
                List.of(), Map.of("datasetId", datasetId));
    }

    @GetMapping("/cliques/insights/shared")
    public SocialNetworkApiResponse<SocialNetworkGroupInsightsService.SharedView> shared(
            @PathVariable String datasetId,
            @RequestParam String first,
            @RequestParam String second,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int pageSize) {
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(),
                service.sharedCliques(datasetId, first, second, offset, pageSize),
                List.of(), Map.of("datasetId", datasetId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SocialNetworkApiResponse<Void>> invalidRequest(
            IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                SocialNetworkApiResponse.error(
                        UUID.randomUUID().toString(),
                        "SN_GROUP_INSIGHTS_BAD_REQUEST",
                        "结论查询参数无效或当前数据集不受支持",
                        Map.of("scope", Map.of())));
    }
}
