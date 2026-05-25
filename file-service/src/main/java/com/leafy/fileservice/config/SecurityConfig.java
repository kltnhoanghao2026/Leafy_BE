package com.leafy.fileservice.config;

import com.leafy.common.config.SecurityProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.WebFilter;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
@Import(SecurityProperties.class)
@Slf4j
public class SecurityConfig {

    private final SecurityProperties securityProperties;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authorizeExchange(exchanges -> {
                    // Permit internal service-to-service communication
                    exchanges.pathMatchers("/internal/**", "/files/upload").permitAll();

                    // Permit base endpoints
                    exchanges.pathMatchers("/").permitAll();
                    exchanges.pathMatchers("/error", "/actuator/**").permitAll();

                    // Swagger/OpenAPI endpoints
                    exchanges.pathMatchers("/v3/api-docs/**").permitAll();
                    exchanges.pathMatchers("/swagger-ui/**").permitAll();
                    exchanges.pathMatchers("/swagger-ui.html").permitAll();
                    exchanges.pathMatchers("/swagger-resources/**").permitAll();
                    exchanges.pathMatchers("/webjars/**").permitAll();

                    // Permit configured public endpoints
                    if (securityProperties.getPublicEndpoints() != null
                            && !securityProperties.getPublicEndpoints().isEmpty()) {
                        exchanges.pathMatchers(securityProperties.getPublicEndpoints().toArray(String[]::new))
                                .permitAll();
                    }

                    // All other requests require authentication
                    exchanges.anyExchange().authenticated();
                })
                .addFilterAt(securityContextFilter(),
                        org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public WebFilter securityContextFilter() {
        return new ReactiveSecurityContextFilter();
    }

}
