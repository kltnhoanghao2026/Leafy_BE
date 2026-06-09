package com.leafy.apigateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SpringDocConfig {

        private static final String BEARER_AUTH_SCHEME = "bearerAuth";

        @Bean
        public OpenAPI gatewayOpenApi() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("Leafy API Gateway")
                                                .description("Gateway API documentation")
                                                .version("v1"))
                                .components(new Components()
                                                .addSecuritySchemes(BEARER_AUTH_SCHEME,
                                                                new SecurityScheme()
                                                                                .name("Authorization")
                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                .scheme("bearer")
                                                                                .bearerFormat("JWT")
                                                                                .in(SecurityScheme.In.HEADER)))
                                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME));
        }

        @Bean
        public List<GroupedOpenApi> apis(RouteDefinitionLocator locator) {
                List<GroupedOpenApi> groups = new ArrayList<>();
                List<RouteDefinition> definitions = locator.getRouteDefinitions().collectList().block();

                if (definitions != null) {
                        definitions.stream()
                                        .filter(routeDefinition -> routeDefinition.getId().matches(
                                                        "auth-service|user-service|farm-service|file-service|notification-service|plant-management-service|profile-service|rag-service|community-feed-service"))
                                        .forEach(routeDefinition -> {
                                                String name = routeDefinition.getId();
                                                groups.add(GroupedOpenApi.builder()
                                                                .pathsToMatch("/" + name + "/**")
                                                                .group(name)
                                                                .build());
                                        });
                }

                // Add grouped APIs for each service manually
                groups.add(GroupedOpenApi.builder()
                                .group("auth-service")
                                .pathsToMatch("/api/auth/**", "/api/devices/**")
                                .build());

                groups.add(GroupedOpenApi.builder()
                                .group("user-service")
                                .pathsToMatch("/api/users/**")
                                .build());

                groups.add(GroupedOpenApi.builder()
                                .group("farm-service")
                                .pathsToMatch("/api/farms/**")
                                .build());

                groups.add(GroupedOpenApi.builder()
                                .group("file-service")
                                .pathsToMatch("/api/files/**")
                                .build());

                groups.add(GroupedOpenApi.builder()
                                .group("notification-service")
                                .pathsToMatch("/api/notifications/**")
                                .build());

                groups.add(GroupedOpenApi.builder()
                                .group("plant-management-service")
                                .pathsToMatch("/api/plants/**")
                                .build());

                groups.add(GroupedOpenApi.builder()
                                .group("profile-service")
                                .pathsToMatch("/api/profiles/**")
                                .build());

                groups.add(GroupedOpenApi.builder()
                                .group("rag-service")
                                .pathsToMatch("/api/rag/**")
                                .build());

                groups.add(GroupedOpenApi.builder()
                                .group("community-feed-service")
                                .pathsToMatch(
                                                "/api/posts/**",
                                                "/api/comments/**",
                                                "/api/votes/**",
                                                "/api/v1/posts/**",
                                                "/api/v1/comments/**",
                                                "/api/v1/votes/**",
                                                "/api/seeder/**")
                                .build());

                return groups;
        }

}
