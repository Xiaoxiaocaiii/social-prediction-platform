package com.prediction.socialnetwork.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SocialNetworkNetworkBusinessGateTest {

    private static final String DATASET =
            "/api/social-network/datasets/think-tank-social-network";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void closesB01ToB05BusinessChainWithRealData() throws Exception {
        mockMvc.perform(get(DATASET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.datasetId").value("think-tank-social-network"));

        mockMvc.perform(get(DATASET + "/network/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCount").value(626))
                .andExpect(jsonPath("$.data.edgeCount").value(12963));

        mockMvc.perform(get(DATASET + "/users/search").param("query", "niwcrso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId")
                        .value("1002259603537702912"));

        mockMvc.perform(get(DATASET + "/network/local")
                        .param("centerNodeId", "1002259603537702912")
                        .param("depth", "1")
                        .param("topK", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("中心节点局部关系网络"))
                .andExpect(jsonPath("$.data.edges").isArray());

        mockMvc.perform(get(DATASET + "/rankings").param("metric", "pagerank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metricType").value("network"))
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get(DATASET + "/users/1002259603537702912"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.networkPosition.pageRank").isNumber())
                .andExpect(jsonPath("$.data.tweetEvidence.scope")
                        .value("当前采集且成功关联的推文样本"));
    }

    @Test
    void pageContainsFiveBusinessSectionsAndDesktopLayoutHooks() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));
        String css = Files.readString(Path.of("src/main/resources/static/css/style.css"));
        String script = Files.readString(Path.of("src/main/resources/static/js/social-network.js"));
        String graphScript = Files.readString(Path.of(
                "src/main/resources/static/js/social-network-graph.js"));
        String uploadSelection = sourceBetween(
                script,
                "function renderTaskUploadSelection(taskCard)",
                "window.onSocialNetworkTaskPageShown = function (task) {");
        String uploadResult = sourceBetween(
                script,
                "function renderTaskUploadResult(view)",
                "function renderTaskUploadState(view)");
        String uploadTaskPersistence = sourceBetween(
                script,
                "function readPersistedUploadTaskId()",
                "function restoreUploadTaskState()");

        assertTrue(html.contains("id=\"page-social-network-task\""));
        assertTrue(html.contains("id=\"social-network-overview-home\""));
        assertTrue(html.contains("id=\"social-network-sidebar\""));
        assertTrue(html.contains("id=\"social-network-user-drawer\""));
        assertFalse(html.contains("id=\"page-social-network\""));
        assertTrue(html.contains("id=\"page-social-network-graph\""));
        assertTrue(css.contains("social-network-local-chart"));
        assertTrue(css.contains("social-network-detail-grid"));
        assertTrue(script.contains("\"network/local\""));
        assertTrue(script.contains("\"rankings\""));
        assertTrue(script.contains("\"users/\""));
        assertTrue(script.contains("workspaceContextApi.setDatasetContext"));
        assertTrue(script.contains("workspaceContextApi.resetDatasetContext"));
        assertTrue(script.contains("datasetVersion"));
        assertTrue(script.contains("sessionId"));
        assertFalse(script.contains("var datasetId ="));
        assertFalse(script.contains("localStorage"));
        assertTrue(script.contains(
                "var UPLOAD_TASK_STORAGE_KEY = \"social-network.upload-task-id.v1\";"));
        assertEquals(3, occurrenceCount(script, "sessionStorage"));
        assertEquals(
                occurrenceCount(script, "sessionStorage"),
                occurrenceCount(uploadTaskPersistence, "sessionStorage"));
        assertTrue(uploadTaskPersistence.contains("UPLOAD_TASK_STORAGE_KEY"));
        assertTrue(uploadTaskPersistence.contains("taskId.trim()"));
        assertFalse(uploadTaskPersistence.contains("datasetId"));
        assertFalse(uploadTaskPersistence.contains("datasetVersion"));
        assertFalse(uploadTaskPersistence.contains("sessionId"));
        assertTrue(graphScript.contains("window.socialNetworkWorkspace.datasetUrl"));
        assertTrue(graphScript.contains("window.socialNetworkWorkspace.requestDataset"));
        assertTrue(graphScript.contains("social-network-dataset-context-changed"));
        assertTrue(graphScript.contains("graphLoadSequence += 1"));
        assertTrue(graphScript.contains("social-network-graph-search-results"));
        assertTrue(graphScript.contains("detailButton.disabled = true"));
        assertFalse(graphScript.contains("var datasetId ="));
        assertTrue(uploadSelection.contains("social-network-task-upload-input"));
        assertTrue(uploadSelection.contains("type=\"file\" multiple"));
        assertTrue(uploadSelection.contains("social-network-task-upload-source"));
        assertTrue(uploadSelection.contains("social-network-upload-responsibility"));
        assertFalse(uploadSelection.contains("type=\"checkbox\""));
        assertTrue(uploadResult.contains("data-upload-result-category"));
        assertTrue(uploadResult.contains("social-network-task-upload-activate"));
        assertTrue(script.contains("/api/social-network/datasets/upload"));
        assertTrue(script.contains("/upload-tasks/"));
        assertTrue(script.contains("role: entry.role"));
        assertTrue(script.contains("capabilitySummary"));
        assertTrue(script.contains("social-network-dataset-switch-builtin"));
        assertTrue(script.contains("SN_DATASET_SESSION_EXPIRED"));
        assertTrue(script.contains("recoverExpiredDatasetContext"));
        assertTrue(script.contains("staleDatasetContext"));
        assertTrue(script.contains("workspaceState.getDatasetRevision()"));
        assertTrue(script.contains("workspaceState.beginRequest"));
        assertTrue(css.contains(".social-network-upload-file-row"));
    }

    @Test
    void uploadFlowKeepsPollingPreviewActivationAndCancellationSeparate() throws Exception {
        String script = Files.readString(Path.of("src/main/resources/static/js/social-network.js"));
        String polling = sourceBetween(
                script, "async function pollUploadTask()", "async function submitUpload(");
        String submission = sourceBetween(
                script, "async function submitUpload(", "async function cancelUploadTask(");
        String cancellation = sourceBetween(
                script, "async function cancelUploadTask()", "function clearUploadTaskState()");
        String activation = sourceBetween(
                script, "async function activateUploadPreview()", "async function editUploadSelection()");
        String failure = sourceBetween(
                script, "function renderTaskUploadResult(view)",
                "function renderTaskUploadState(view)");

        assertTrue(polling.contains("/upload-tasks/"));
        assertTrue(polling.contains("renderUploadTask(body.data)"));
        assertTrue(polling.contains("scheduleUploadTaskPoll"));
        assertTrue(submission.contains("scheduleUploadTaskPoll(350)"));
        assertFalse(submission.contains("setDatasetContext"));
        assertTrue(cancellation.contains("method: \"DELETE\""));
        assertTrue(cancellation.contains("renderUploadTask(body.data)"));
        assertTrue(cancellation.contains("scheduleUploadTaskPoll(600)"));
        assertTrue(activation.contains("taskView.status !== \"ready\""));
        assertTrue(activation.contains("workspaceContextApi.setDatasetContext(next)"));
        assertTrue(activation.contains("workspaceContextApi.setDatasetContext(previous)"));
        assertTrue(activation.contains("sameDatasetContext(readDatasetContext(), next)"));
        assertTrue(failure.contains("失败结果不会改变当前分析数据"));
        assertTrue(failure.contains("返回修改文件"));
    }

    private static String sourceBetween(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start, "Expected source section was not found");
        return source.substring(start, end);
    }

    private static int occurrenceCount(String source, String value) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }
}
