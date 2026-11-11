package com.prediction.socialnetwork.application;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social-network/datasets/{datasetId}/network")
public class SocialNetworkGroupExportController {

    private final SocialNetworkGroupExportService exportService;

    public SocialNetworkGroupExportController(SocialNetworkGroupExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/exports")
    public ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> export(
            @PathVariable String datasetId,
            @RequestParam(required = false) String datasetVersion,
            @RequestParam(required = false) String sessionId,
            @RequestBody SocialNetworkGroupExportService.ExportRequest request) {
        try {
            SocialNetworkGroupExportService.ExportResult result =
                    datasetVersion == null && sessionId == null
                            ? exportService.export(datasetId, request)
                            : exportService.export(
                                    datasetId, datasetVersion, sessionId, request);
            String requestId = UUID.randomUUID().toString();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("exportId", requestId);
            data.put("exportType", result.exportType());
            data.put("status", "completed");
            data.put("statusLabel", "已完成");
            data.put("downloadAvailable", false);
            data.put("format", result.format());
            data.put("contentType", result.contentType());
            data.put("contentLength", result.contentLength());
            data.put("metadata", result.metadata());
            data.put("result", result.result());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("datasetId", datasetId);
            meta.put("datasetVersion", datasetVersion == null ? "" : datasetVersion);
            meta.put("sessionId", sessionId == null ? "" : sessionId);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    SocialNetworkApiResponse.success(
                            requestId,
                            data,
                            List.of(),
                            meta));
        } catch (SocialNetworkDatasetContextException exception) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "SN_DATASET_CONTEXT_INVALID",
                    "数据集版本或上传会话与当前请求不匹配。",
                    datasetId);
        } catch (SocialNetworkDatasetSessionExpiredException exception) {
            return error(
                    HttpStatus.GONE,
                    "SN_DATASET_SESSION_EXPIRED",
                    "上传数据会话已失效，请重新上传数据。",
                    datasetId);
        } catch (IllegalArgumentException exception) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "SN_BAD_REQUEST",
                    "导出参数无效：" + exception.getMessage(),
                    datasetId);
        } catch (SocialNetworkGroupExportService.EmptyResultException exception) {
            return error(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "SN_EXPORT_EMPTY_RESULT",
                    "当前分析尚无可导出的结果。",
                    datasetId);
        } catch (SocialNetworkGroupExportService.ExportTooLargeException exception) {
            return error(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "SN_EXPORT_TOO_LARGE",
                    "导出结果超过单次响应上限（2 MiB），未生成部分文件。",
                    datasetId);
        } catch (SocialNetworkGroupExportService.AnalysisRunningException exception) {
            return error(
                    HttpStatus.CONFLICT,
                    "SN_GROUP_EXPORT_NOT_READY",
                    "分析仍在运行，请完成后再导出。",
                    datasetId);
        } catch (IllegalStateException exception) {
            return error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SN_GROUP_EXPORT_UNAVAILABLE",
                    "当前分析或来源证据发生变化，未生成导出文件。",
                    datasetId);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> invalidRequest(
            IllegalArgumentException exception) {
        return error(
                HttpStatus.BAD_REQUEST,
                "SN_BAD_REQUEST",
                "导出参数无效。",
                "");
    }

    private static ResponseEntity<SocialNetworkApiResponse<Map<String, Object>>> error(
            HttpStatus status,
            String code,
            String message,
            String datasetId) {
        Map<String, Object> meta = datasetId == null || datasetId.isBlank()
                ? Map.of("scope", Map.of())
                : Map.of("datasetId", datasetId);
        return ResponseEntity.status(status).body(
                SocialNetworkApiResponse.error(
                        UUID.randomUUID().toString(), code, message, meta));
    }
}
