package com.prediction.socialnetwork.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social-network")
public class SocialNetworkUploadTaskController {

    private final SocialNetworkUploadTaskService uploadTasks;

    public SocialNetworkUploadTaskController(SocialNetworkUploadTaskService uploadTasks) {
        this.uploadTasks = uploadTasks;
    }

    @GetMapping("/datasets/upload-tasks/{uploadTaskId}")
    public ResponseEntity<SocialNetworkApiResponse<SocialNetworkUploadTaskView>> getTask(
            @PathVariable String uploadTaskId) {
        try {
            SocialNetworkUploadTaskView task = uploadTasks.getTask(uploadTaskId);
            return ResponseEntity.ok(success(task, taskMeta(task)));
        } catch (SocialNetworkUploadTaskExpiredException exception) {
            return error(
                    HttpStatus.GONE,
                    "SN_UPLOAD_TASK_EXPIRED",
                    "上传任务记录已过期，请重新上传");
        } catch (IllegalArgumentException exception) {
            return error(
                    HttpStatus.NOT_FOUND,
                    "SN_UPLOAD_TASK_NOT_FOUND",
                    "未找到上传任务");
        }
    }

    @DeleteMapping("/datasets/upload-tasks/{uploadTaskId}")
    public ResponseEntity<SocialNetworkApiResponse<SocialNetworkUploadTaskView>> cancelTask(
            @PathVariable String uploadTaskId) {
        try {
            SocialNetworkUploadTaskView task = uploadTasks.cancelTask(uploadTaskId);
            HttpStatus status = "cancelling".equals(task.status())
                    ? HttpStatus.ACCEPTED
                    : HttpStatus.OK;
            return ResponseEntity.status(status).body(success(task, taskMeta(task)));
        } catch (SocialNetworkUploadTaskExpiredException exception) {
            return error(
                    HttpStatus.GONE,
                    "SN_UPLOAD_TASK_EXPIRED",
                    "上传任务记录已过期，请重新上传");
        } catch (IllegalArgumentException exception) {
            return error(
                    HttpStatus.NOT_FOUND,
                    "SN_UPLOAD_TASK_NOT_FOUND",
                    "未找到上传任务");
        } catch (IllegalStateException exception) {
            return error(
                    HttpStatus.CONFLICT,
                    "SN_UPLOAD_TASK_STATE_CONFLICT",
                    "当前上传任务状态不允许取消");
        }
    }

    @DeleteMapping("/datasets/{datasetId}/sessions/{sessionId}")
    public ResponseEntity<SocialNetworkApiResponse<Map<String, String>>> closeSession(
            @PathVariable String datasetId,
            @PathVariable String sessionId,
            @RequestParam String datasetVersion) {
        try {
            uploadTasks.closeSession(datasetId, datasetVersion, sessionId);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("datasetId", datasetId);
            meta.put("datasetVersion", datasetVersion);
            meta.put("scope", Map.of());
            return ResponseEntity.ok(success(
                    Map.of("status", "closed"),
                    meta));
        } catch (SocialNetworkDatasetContextException exception) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "SN_DATASET_CONTEXT_MISMATCH",
                    "数据集上下文不匹配");
        } catch (SocialNetworkDatasetSessionExpiredException
                | IllegalArgumentException exception) {
            return error(
                    HttpStatus.GONE,
                    "SN_DATASET_SESSION_EXPIRED",
                    "上传分析会话已失效");
        }
    }

    private static Map<String, Object> taskMeta(SocialNetworkUploadTaskView task) {
        Map<String, Object> meta = new LinkedHashMap<>();
        SocialNetworkUploadTaskView.Preview preview = task.preview();
        meta.put("datasetId", preview == null ? null : preview.datasetId());
        meta.put("datasetVersion", preview == null ? null : preview.datasetVersion());
        meta.put("scope", Map.of());
        return meta;
    }

    private static <T> SocialNetworkApiResponse<T> success(
            T data, Map<String, Object> meta) {
        return SocialNetworkApiResponse.success(
                UUID.randomUUID().toString(),
                data,
                List.of(),
                meta);
    }

    private static <T> ResponseEntity<SocialNetworkApiResponse<T>> error(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(SocialNetworkApiResponse.error(
                UUID.randomUUID().toString(),
                code,
                message,
                Map.of("datasetId", "", "datasetVersion", "", "scope", Map.of())));
    }
}
