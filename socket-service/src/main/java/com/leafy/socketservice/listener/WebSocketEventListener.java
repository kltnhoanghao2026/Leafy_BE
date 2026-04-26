package com.leafy.socketservice.listener;

import com.leafy.socketservice.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Listens for STOMP disconnect events to automatically mark users as OFFLINE
 * even when the client closes the connection abruptly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final UserPresenceService userPresenceService;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        if (headerAccessor.getSessionAttributes() == null) return;

        String userId = (String) headerAccessor.getSessionAttributes().get("userId");
        if (userId != null) {
            log.info("[WS] User disconnected: {}", userId);
            userPresenceService.disconnect(userId);
        }
    }
}
