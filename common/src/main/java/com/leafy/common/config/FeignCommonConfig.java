package com.leafy.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;

/**
 * Common Feign configuration shared across all microservices.
 *
 * <p>To activate, include the {@code common} module on the classpath and add
 * {@code @EnableFeignClients} to your service's main application class. Spring
 * Boot auto-configuration will pick up this {@link Configuration} automatically
 * via component scanning (the common module's base package must be included in
 * the scan, or you can explicitly import this class).
 *
 * <p><b>Usage in a microservice:</b>
 * <pre>
 * &#64;SpringBootApplication
 * &#64;EnableFeignClients
 * public class MyServiceApplication { ... }
 * </pre>
 *
 * Individual Feign client methods no longer need manual {@code @RequestHeader}
 * parameters for {@code X-User-Id}, {@code X-User-Email}, {@code X-User-Roles},
 * or {@code X-Profile-Id} — {@link FeignSecurityInterceptor} injects them
 * automatically from the current thread's security context.
 */
@Configuration
@ConditionalOnClass({feign.RequestInterceptor.class, GrantedAuthority.class})
public class FeignCommonConfig {

    @Bean
    public FeignSecurityInterceptor feignSecurityInterceptor() {
        return new FeignSecurityInterceptor();
    }
}
