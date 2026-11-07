package com.prediction.socialnetwork.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.MultipartConfigElement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SocialNetworkUploadServletConfiguration {

    private static final long MAX_UPLOAD_FILE_BYTES = 128L * 1024 * 1024;
    private static final long MAX_UPLOAD_REQUEST_BYTES = 160L * 1024 * 1024;

    @Bean
    public ServletRegistrationBean<SocialNetworkUploadServlet> socialNetworkUploadServlet(
            SocialNetworkUploadTaskService uploadTasks,
            SocialNetworkUploadTemporaryFileCleanup temporaryFileCleanup,
            ObjectMapper objectMapper) {
        Path multipartLocation = Path.of(
                System.getProperty("java.io.tmpdir"),
                "social-network-analysis-http-uploads").toAbsolutePath();
        try {
            Files.createDirectories(multipartLocation);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "unable to initialize upload multipart storage", exception);
        }
        MultipartConfigElement multipartConfig = new MultipartConfigElement(
                multipartLocation.toString(),
                MAX_UPLOAD_FILE_BYTES,
                MAX_UPLOAD_REQUEST_BYTES,
                0);
        ServletRegistrationBean<SocialNetworkUploadServlet> registration =
                new ServletRegistrationBean<>(
                        new SocialNetworkUploadServlet(
                                uploadTasks, temporaryFileCleanup, objectMapper, multipartLocation),
                        "/api/social-network/datasets/upload");
        registration.setName("socialNetworkUploadServlet");
        registration.setLoadOnStartup(1);
        registration.setMultipartConfig(multipartConfig);
        return registration;
    }
}
