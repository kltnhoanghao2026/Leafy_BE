package com.leafy.iotmetricscollectorservice.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ImageAnalysisQueueConfig {

    @Bean(name = "imageAnalysisTaskExecutor")
    public Executor imageAnalysisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("image-analysis-");
        executor.initialize();
        return executor;
    }
}
