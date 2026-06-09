package com.leafy.common.config;

import com.leafy.common.security.UserPrincipal;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Feign {@link RequestInterceptor} that automatically propagates the current
 * user's security context headers to every outgoing Feign request.
 *
 * <p>This eliminates the need to declare {@code @RequestHeader} parameters on
 * every Feign client method. Register once via {@link FeignCommonConfig} and all
 * Feign clients in the service will pick it up automatically.
 *
 * <p>Headers propagated:
 * <ul>
 *   <li>{@code X-User-Id}     — the authenticated account / user ID</li>
 *   <li>{@code X-User-Email}  — the authenticated user's email</li>
 *   <li>{@code X-User-Roles}  — comma-separated granted authorities</li>
 *   <li>{@code X-Profile-Id}  — the active profile ID</li>
 * </ul>
 *
 * <p>If there is no authenticated user in the current thread (e.g. background
 * tasks, scheduled jobs), the headers are simply not added and a warning is
 * logged so downstream services can still handle the request gracefully.
 */
@Slf4j
public class FeignSecurityInterceptor implements RequestInterceptor {

    private static final String HEADER_USER_ID    = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_PROFILE_ID = "X-Profile-Id";

    @Override
    public void apply(RequestTemplate template) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            log.debug("FeignSecurityInterceptor: no authenticated UserPrincipal in context — skipping header propagation");
            return;
        }

        // Propagate user identity headers
        setHeaderIfPresent(template, HEADER_USER_ID,    principal.getUserId());
        setHeaderIfPresent(template, HEADER_USER_EMAIL, principal.getEmail());
        setHeaderIfPresent(template, HEADER_PROFILE_ID, principal.getProfileId());

        // Build roles string from granted authorities
        String roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        template.header(HEADER_USER_ROLES, roles);

        log.debug("FeignSecurityInterceptor: propagating headers for user={} profile={}",
                principal.getUserId(), principal.getProfileId());
    }

    private void setHeaderIfPresent(RequestTemplate template, String name, String value) {
        if (value != null && !value.isBlank()) {
            template.header(name, value);
        }
    }
}
