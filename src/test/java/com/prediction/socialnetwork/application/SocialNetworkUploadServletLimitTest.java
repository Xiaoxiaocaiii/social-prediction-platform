package com.prediction.socialnetwork.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SocialNetworkUploadServletLimitTest {

    private static final long MAX_FILE_BYTES = 128L * 1024 * 1024;
    private static final long MAX_REQUEST_BYTES = 160L * 1024 * 1024;

    @TempDir
    Path tempDir;

    @Test
    void rejectsFileJustOverLimitAndCleansRequestStaging() throws Exception {
        SocialNetworkUploadTaskService uploadTasks = mock(SocialNetworkUploadTaskService.class);
        SocialNetworkUploadTemporaryFileCleanup cleanup =
                new SocialNetworkUploadTemporaryFileCleanup(
                        tempDir.resolve("http-uploads"),
                        tempDir.resolve("task-uploads"),
                        java.time.Clock.systemUTC());
        Path requestRoot = Files.createDirectories(tempDir.resolve("http-uploads"));
        SocialNetworkUploadServlet servlet = new SocialNetworkUploadServlet(
                uploadTasks, cleanup, new ObjectMapper(), requestRoot);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ResponseCapture response = new ResponseCapture();
        Part manifest = manifestPart();
        Part file = filePart("user-part", "users.csv", MAX_FILE_BYTES + 1);
        when(request.getContentLengthLong()).thenReturn(MAX_FILE_BYTES + 1024);
        when(request.getParts()).thenReturn(List.of(manifest, file));

        servlet.doPost(request, response.response);

        assertEquals(413, response.status);
        assertTrue(response.body().contains("SN_BAD_REQUEST"));
        try (var entries = Files.list(requestRoot)) {
            assertEquals(0, entries.count());
        }
        verify(manifest).delete();
        verify(file).delete();
        verify(uploadTasks, never()).submit(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsRequestJustOverLimitBeforeParsingMultipart() throws Exception {
        SocialNetworkUploadServlet servlet = new SocialNetworkUploadServlet(
                mock(SocialNetworkUploadTaskService.class),
                new SocialNetworkUploadTemporaryFileCleanup(
                        tempDir.resolve("http-request-limit"),
                        tempDir.resolve("task-request-limit"),
                        java.time.Clock.systemUTC()),
                new ObjectMapper(),
                tempDir.resolve("http-request-limit"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        ResponseCapture response = new ResponseCapture();
        when(request.getContentLengthLong()).thenReturn(MAX_REQUEST_BYTES + 1);

        servlet.doPost(request, response.response);

        assertEquals(413, response.status);
        assertTrue(response.body().contains("SN_BAD_REQUEST"));
        assertFalse(Files.exists(tempDir.resolve("http-request-limit")));
        verify(request, never()).getParts();
    }

    private static Part manifestPart() throws Exception {
        Part manifest = mock(Part.class);
        when(manifest.getName()).thenReturn("manifest");
        when(manifest.getSize()).thenReturn(57L);
        when(manifest.getInputStream()).thenReturn(new ByteArrayInputStream(
                "{\"files\":[{\"partId\":\"user-part\",\"role\":\"users\"}]}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return manifest;
    }

    private static Part filePart(String partId, String fileName, long size) throws Exception {
        Part file = mock(Part.class);
        when(file.getName()).thenReturn(partId);
        when(file.getSubmittedFileName()).thenReturn(fileName);
        when(file.getSize()).thenReturn(size);
        return file;
    }

    private static final class ResponseCapture {
        private final ByteArrayOutputStream content = new ByteArrayOutputStream();
        private final HttpServletResponse response = mock(HttpServletResponse.class);
        private int status;

        private ResponseCapture() throws IOException {
            when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                }

                @Override
                public void write(int value) {
                    content.write(value);
                }
            });
            org.mockito.Mockito.doAnswer(invocation -> {
                status = invocation.getArgument(0);
                return null;
            }).when(response).setStatus(org.mockito.ArgumentMatchers.anyInt());
        }

        private String body() {
            return content.toString(StandardCharsets.UTF_8);
        }
    }
}
