package com.leafy.notificationservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sendinblue.ApiClient;
import sibApi.TransactionalEmailsApi;

@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "brevo.enabled", havingValue = "true", matchIfMissing = true)
public class BrevoConfig {

    private final BrevoProperties brevoProperties;

    @Bean
    public ApiClient brevoApiClient() {
        log.info("Initializing Brevo API Client");
        ApiClient apiClient = sendinblue.Configuration.getDefaultApiClient();
        apiClient.setApiKey(brevoProperties.getApiKey());
        
        // Set connection and read timeout to 60 seconds
        apiClient.setConnectTimeout(60000);
        apiClient.setReadTimeout(60000);
        
        log.info("Brevo API Client configured with 60s timeout");
        return apiClient;
    }

    @Bean
    public TransactionalEmailsApi transactionalEmailsApi(ApiClient apiClient) {
        log.info("Initializing Brevo Transactional Emails API");
        return new TransactionalEmailsApi(apiClient);
    }
}
