package com.leafy.plantmanagementservice.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "perenual.api")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PerenualApiProperties {
    String baseUrl = "https://perenual.com/api";
    String key;
}
