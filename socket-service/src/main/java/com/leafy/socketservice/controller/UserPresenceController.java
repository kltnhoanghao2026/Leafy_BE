package com.leafy.socketservice.controller;

import com.leafy.common.security.UserPrincipal;
import com.leafy.socketservice.model.ChatUser;
import com.leafy.socketservice.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class UserPresenceController {

    private final UserPresenceService userPresenceService;

    /**
     * Client sends: /app/user.addUser after STOMP CONNECT.
     * User identity is derived from the authenticated Principal (populated by JwtChannelInterceptor),
     * so the payload body is not required.
     */
    @MessageMapping("/user.addUser")
    public void addUser(
            @Payload(required = false) ChatUser payload,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal) {

        // Extract userId from the authenticated STOMP Principal
        String userId = null;
        String email = null;
        String profileId = null;

        if (principal instanceof UsernamePasswordAuthenticationToken authToken
                && authToken.getPrincipal() instanceof UserPrincipal userPrincipal) {
            userId    = userPrincipal.getUserId();
            email     = userPrincipal.getEmail();
            profileId = userPrincipal.getProfileId();
        } else if (principal != null) {
            userId = principal.getName();
        }

        if (userId == null) {
            log.warn("[WS] addUser called with no authenticated principal – ignoring");
            return;
        }

        // Build ChatUser from JWT claims; let payload override optional fields
        ChatUser user = ChatUser.builder()
                .id(userId)
                .email(email)
                .profileId(profileId)
                .fullName(payload != null && payload.getFullName() != null ? payload.getFullName() : null)
                .avatar(payload != null ? payload.getAvatar() : null)
                .build();

        ChatUser savedUser = userPresenceService.saveUser(user);
        headerAccessor.getSessionAttributes().put("userId", savedUser.getId());
        log.info("[WS] User {} registered for presence tracking", userId);
    }

    /**
     * Client sends: /app/user.disconnectUser to explicitly go offline.
     */
    @MessageMapping("/user.disconnectUser")
    public void disconnectUser(@Payload ChatUser user) {
        userPresenceService.disconnect(user.getId());
    }
}
