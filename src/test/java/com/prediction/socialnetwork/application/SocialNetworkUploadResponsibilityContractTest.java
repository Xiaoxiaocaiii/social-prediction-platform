package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SocialNetworkUploadResponsibilityContractTest {

    @Test
    void responsibilityPromptIsInformationalAndSourceDescriptionIsOptional() throws IOException {
        String script = Files.readString(Path.of(
                "src/main/resources/static/js/social-network.js").toAbsolutePath());
        int formStart = script.indexOf("function renderTaskUploadSelection(taskCard)");
        int formEnd = script.indexOf(
                "window.onSocialNetworkTaskPageShown = function (task) {", formStart);
        assertTrue(formStart >= 0 && formEnd > formStart);
        String uploadForm = script.substring(formStart, formEnd);

        assertTrue(uploadForm.contains("id=\"social-network-task-upload-source\""));
        assertTrue(uploadForm.contains("可留空"));
        assertTrue(uploadForm.contains("平台不核验数据来源或授权"));
        assertTrue(uploadForm.contains("仅用于当前分析会话，不会永久保存"));
        assertTrue(uploadForm.contains("无需勾选确认"));
        assertFalse(Pattern.compile(
                "(?is)<input\\b[^>]*\\btype\\s*=\\s*['\"]checkbox['\"][^>]*>")
                .matcher(uploadForm)
                .find());

        int gateStart = script.indexOf("function uploadSelectionIssue()");
        int gateEnd = script.indexOf("function uploadTaskIsBusy()", gateStart);
        assertTrue(gateStart >= 0 && gateEnd > gateStart);
        String selectionGate = script.substring(gateStart, gateEnd);
        assertFalse(selectionGate.contains("social-network-upload-source"));
        assertFalse(selectionGate.contains("responsibility"));
        assertTrue(script.contains(
                "uploadState.uploadDraft.sourceDescription = sourceInput.value;"));
        int submitStart = script.indexOf("async function submitUpload(event)");
        int submitEnd = script.indexOf("\n    async function cancelUploadTask()", submitStart);
        assertTrue(submitStart >= 0 && submitEnd > submitStart);
        String submitUpload = script.substring(submitStart, submitEnd);
        assertTrue(submitUpload.contains(
                "var sourceDescription = uploadState.uploadDraft.sourceDescription;"));
        assertTrue(submitUpload.contains("sourceDescription: sourceDescription"));
        assertTrue(submitUpload.contains(
                "formData.append(\"manifest\", JSON.stringify({"));
    }
}
