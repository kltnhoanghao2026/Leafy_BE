package com.leafy.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI leafyOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Leafy API")
                        .description("Leafy Microservices API Documentation")
                        .version("v0.0.1")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")));
    }
}
