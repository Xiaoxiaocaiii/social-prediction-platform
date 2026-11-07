package com.prediction.socialnetwork.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class SocialNetworkUploadExecutorConfiguration {

    @Bean(name = "socialNetworkUploadExecutor")
    public ThreadPoolTaskExecutor socialNetworkUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("social-network-upload-");
        return executor;
    }
}
