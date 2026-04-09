package com.leafy.notificationservice.controller;

import com.leafy.notificationservice.dto.RegisterPushTokenRequest;
import com.leafy.notificationservice.service.PushTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @PostMapping
    public ResponseEntity<String> register(@Valid @RequestBody RegisterPushTokenRequest request) {
        pushTokenService.registerToken(request);
        return ResponseEntity.ok("Push token registered successfully");
    }
}