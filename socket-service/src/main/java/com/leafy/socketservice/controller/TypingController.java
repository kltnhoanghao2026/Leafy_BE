package com.leafy.socketservice.controller;

import com.leafy.socketservice.dto.TypingPayload;
import com.leafy.socketservice.service.TypingService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class TypingController {

    private final TypingService typingService;

    /**
     * Client sends: /app/chat.typing with a TypingPayload.
     * The userId is resolved server-side from the STOMP session (trusted source),
     * not from the client-supplied payload.
     */
    @MessageMapping("/chat.typing")
    public void typing(
            @Payload TypingPayload payload,
            SimpMessageHeaderAccessor headerAccessor) {
        String senderId = (String) headerAccessor.getSessionAttributes().get("userId");
        if (senderId == null || payload.conversationId() == null) return;

        // Build a new payload with the resolved senderId to prevent spoofing
        TypingPayload enriched = new TypingPayload(
                payload.conversationId(),
                senderId,
                payload.userName(),
                payload.isTyping(),
                payload.platform()
        );
        typingService.broadcast(enriched, senderId);
    }
}
