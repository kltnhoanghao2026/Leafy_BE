package com.leafy.iottestdataservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SeedProperties.class)
public class SeedConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder, SeedProperties seedProperties) {
        return builder.baseUrl(seedProperties.getCollector().getBaseUrl()).build();
    }

    @Bean
    public ThreadPoolTaskScheduler seedTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("iot-seed-");
        scheduler.initialize();
        return scheduler;
    }
}
