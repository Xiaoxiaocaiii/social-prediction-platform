package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prediction.socialnetwork.domain.DatasetFileRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class SocialNetworkUploadTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SocialNetworkUploadTaskService uploadTasks;

    @Autowired
    private SocialNetworkDatasetRegistry registry;

    @TempDir
    private Path tempDir;

    @Test
    void exposesTaskPreviewAndIdempotentSessionClose() throws Exception {
        Path usersFile = tempDir.resolve("users.csv");
        Files.writeString(usersFile, "user_id,username\ncontroller-user,controller\n");
        SocialNetworkUploadTaskView submitted = uploadTasks.submit(
                List.of(new SocialNetworkUploadFileInput(
                        "users-part", "users.csv", DatasetFileRole.USERS, usersFile)),
                "controller test");
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertEquals(
                        "ready", uploadTasks.getTask(submitted.uploadTaskId()).status()));

        SocialNetworkUploadTaskView task = uploadTasks.getTask(submitted.uploadTaskId());
        String sessionPath = "/api/social-network/datasets/" + task.preview().datasetId()
                + "/sessions/" + task.preview().sessionId();
        String taskPath = "/api/social-network/datasets/upload-tasks/"
                + submitted.uploadTaskId();

        mockMvc.perform(get(taskPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ready"))
                .andExpect(jsonPath("$.data.preview.type").value("upload_session"))
                .andExpect(jsonPath("$.data.preview.datasetVersion")
                        .value(task.preview().datasetVersion()))
                .andExpect(jsonPath("$.data.preview.sourceDescription").doesNotExist())
                .andExpect(jsonPath("$.data.preview.files[0].role").value("USERS"));
        assertEquals("controller test",
                registry.findDataset(task.preview().datasetId()).sourceDescription());

        mockMvc.perform(delete(taskPath))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SN_UPLOAD_TASK_STATE_CONFLICT"));

        mockMvc.perform(delete(sessionPath)
                        .param("datasetVersion", task.preview().datasetVersion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("closed"));
        mockMvc.perform(delete(sessionPath)
                        .param("datasetVersion", task.preview().datasetVersion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("closed"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.findDataset(task.preview().datasetId()));
    }

    @Test
    void returnsContractErrorsForUnknownTasksAndSessions() throws Exception {
        mockMvc.perform(get(
                        "/api/social-network/datasets/upload-tasks/missing-task"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SN_UPLOAD_TASK_NOT_FOUND"));

        mockMvc.perform(delete(
                        "/api/social-network/datasets/upload-tasks/missing-task"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SN_UPLOAD_TASK_NOT_FOUND"));

        mockMvc.perform(delete(
                        "/api/social-network/datasets/missing-dataset/sessions/missing-session")
                        .param("datasetVersion", "missing-version"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SN_DATASET_SESSION_EXPIRED"));
    }

    @Test
    void returnsGoneAfterAnUploadTaskRecordHasBeenRemoved() throws Exception {
        SocialNetworkUploadTaskService expiredService =
                mock(SocialNetworkUploadTaskService.class);
        when(expiredService.getTask("expired-record"))
                .thenThrow(new SocialNetworkUploadTaskExpiredException());
        when(expiredService.cancelTask("expired-record"))
                .thenThrow(new SocialNetworkUploadTaskExpiredException());
        MockMvc isolatedMvc = MockMvcBuilders.standaloneSetup(
                new SocialNetworkUploadTaskController(expiredService)).build();

        isolatedMvc.perform(get(
                        "/api/social-network/datasets/upload-tasks/expired-record"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SN_UPLOAD_TASK_EXPIRED"));
        isolatedMvc.perform(delete(
                        "/api/social-network/datasets/upload-tasks/expired-record"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SN_UPLOAD_TASK_EXPIRED"));
    }

    @Test
    void returnsFileValidationFailureAsACompletedTaskEnvelope() throws Exception {
        Path invalidFile = tempDir.resolve("invalid-users.csv");
        Files.writeString(invalidFile, "unknown_column\nvalue\n");
        SocialNetworkUploadTaskView submitted = uploadTasks.submit(
                List.of(new SocialNetworkUploadFileInput(
                        "invalid-users", "invalid-users.csv", DatasetFileRole.USERS, invalidFile)),
                null);
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertEquals(
                        "failed", uploadTasks.getTask(submitted.uploadTaskId()).status()));

        mockMvc.perform(get("/api/social-network/datasets/upload-tasks/"
                        + submitted.uploadTaskId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.meta.scope").isMap())
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.error.code").value("SN_UPLOAD_FILE_BLOCKED"))
                .andExpect(jsonPath("$.data.error.issues[0].fileName")
                        .value("invalid-users.csv"))
                .andExpect(jsonPath("$.data.preview").doesNotExist());
        assertEquals("think-tank-social-network", registry.currentDataset().datasetId());
    }

    @Test
    void returnsExpiredTaskStateUsingTheUnifiedSuccessEnvelope() throws Exception {
        SocialNetworkUploadTaskService expiredService =
                mock(SocialNetworkUploadTaskService.class);
        when(expiredService.getTask("expired-task")).thenReturn(
                new SocialNetworkUploadTaskView(
                        "expired-task",
                        "expired",
                        null,
                        Instant.parse("2026-10-17T12:00:00Z"),
                        null,
                        new SocialNetworkUploadTaskView.Failure(
                                "SN_UPLOAD_TASK_EXPIRED",
                                "上传任务对应的分析预览已过期，请重新上传。",
                                List.of())));
        MockMvc isolatedMvc = MockMvcBuilders.standaloneSetup(
                new SocialNetworkUploadTaskController(expiredService)).build();

        isolatedMvc.perform(get(
                        "/api/social-network/datasets/upload-tasks/expired-task"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.meta.scope").isMap())
                .andExpect(jsonPath("$.data.status").value("expired"))
                .andExpect(jsonPath("$.data.error.code").value("SN_UPLOAD_TASK_EXPIRED"));
    }
}
