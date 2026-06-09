package com.leafy.fileservice.config;

import com.leafy.common.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reactive Security Context Filter
 * Extracts user info from headers and sets security context
 */
@Slf4j
public class ReactiveSecurityContextFilter implements WebFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_PROFILE_ID = "X-Profile-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String userId = request.getHeaders().getFirst(HEADER_USER_ID);
        String email = request.getHeaders().getFirst(HEADER_USER_EMAIL);
        String rolesHeader = request.getHeaders().getFirst(HEADER_USER_ROLES);
        String profileId = request.getHeaders().getFirst(HEADER_PROFILE_ID);

        if (userId != null && email != null) {
            try {
                List<GrantedAuthority> authorities = parseRoles(rolesHeader);

                // Create UserPrincipal with available info
                // We pass null for fields not available in headers or not needed here yet
                UserPrincipal userPrincipal = new UserPrincipal(
                        userId,
                        email,
                        null, // jti
                        null, // deviceId
                        null, // userAgent
                        null, // requestDeviceId
                        null, // remainingTTL
                        profileId, // profileId
                        authorities);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userPrincipal,
                        null,
                        authorities);

                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

            } catch (Exception e) {
                log.error("Error setting security context: {}", e.getMessage());
            }
        }

        return chain.filter(exchange);
    }

    private List<GrantedAuthority> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.trim().isEmpty()) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> {
                    String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                    return new SimpleGrantedAuthority(roleWithPrefix);
                })
                .collect(Collectors.toList());
    }
}
