package com.leafy.notificationservice.controller;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.leafy.notificationservice.dto.TestPushRequest;
import com.leafy.notificationservice.service.FirebasePushService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/push")
@RequiredArgsConstructor
public class InternalPushController {

    private final FirebasePushService firebasePushService;

    @PostMapping("/test")
    public ResponseEntity<String> test(@Valid @RequestBody TestPushRequest request)
            throws FirebaseMessagingException {

        String messageId = firebasePushService.sendToToken(
                request.getToken(),
                request.getTitle(),
                request.getBody(),
                Map.of("type", "TEST_PUSH")
        );

        return ResponseEntity.ok(messageId);
    }
}