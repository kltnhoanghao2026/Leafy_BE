package com.leafy.apigateway.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SpringDocConfig {

        @Bean
        public List<GroupedOpenApi> apis(RouteDefinitionLocator locator) {
                List<GroupedOpenApi> groups = new ArrayList<>();
                List<RouteDefinition> definitions = locator.getRouteDefinitions().collectList().block();

                if (definitions != null) {
                        definitions.stream()
                                        .filter(routeDefinition -> routeDefinition.getId().matches(
                                                        "auth-service|user-service|farm-service|file-service|notification-service|plant-management-service|profile-service"))
                                        .forEach(routeDefinition -> {
                                                String name = routeDefinition.getId();
                                                GroupedOpenApi.builder()
                                                                .pathsToMatch("/" + name + "/**")
                                                                .group(name)
                                                                .build();
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
                                .pathsToMatch("/api/profiles/**", "/api/preferences/**")
                                .build());

                return groups;
        }

}
