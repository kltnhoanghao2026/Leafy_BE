package com.leafy.socketservice.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Logs WebSocket session lifecycle events.
 */
@Component
@Slf4j
public class WebSocketEventListener {

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        String user = event.getUser() != null ? event.getUser().getName() : "anonymous";
        log.info("[WS] Client connected – sessionId={} user={}", sessionId, user);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String user = event.getUser() != null ? event.getUser().getName() : "anonymous";
        log.info("[WS] Client disconnected – sessionId={} user={}", sessionId, user);
    }
}
