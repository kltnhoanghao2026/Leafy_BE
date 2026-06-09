package com.leafy.notificationservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "brevo")
public class BrevoProperties {

    private String apiKey;

    private String defaultSenderEmail;

    private String defaultSenderName;

    private boolean enabled = true;
}
