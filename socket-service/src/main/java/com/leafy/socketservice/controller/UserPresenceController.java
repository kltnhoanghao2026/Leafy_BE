package com.leafy.socketservice.controller;

import com.leafy.socketservice.model.ChatUser;
import com.leafy.socketservice.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class UserPresenceController {

    private final UserPresenceService userPresenceService;

    /**
     * Client sends: /app/user.addUser with a ChatUser payload after STOMP CONNECT.
     * Stores the user's userId in the session for later disconnect handling.
     */
    @MessageMapping("/user.addUser")
    public void addUser(
            @Payload ChatUser user,
            SimpMessageHeaderAccessor headerAccessor) {
        ChatUser savedUser = userPresenceService.saveUser(user);
        headerAccessor.getSessionAttributes().put("userId", savedUser.getId());
    }

    /**
     * Client sends: /app/user.disconnectUser to explicitly go offline.
     */
    @MessageMapping("/user.disconnectUser")
    public void disconnectUser(@Payload ChatUser user) {
        userPresenceService.disconnect(user.getId());
    }
}
