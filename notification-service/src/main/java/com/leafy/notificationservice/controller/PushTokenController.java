package com.leafy.notificationservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.notificationservice.dto.request.DeactivatePushTokenRequest;
import com.leafy.notificationservice.dto.request.RegisterPushTokenRequest;
import com.leafy.notificationservice.service.token.PushTokenService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/push-tokens")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PushTokenController {

    PushTokenService pushTokenService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterPushTokenRequest request) {
        pushTokenService.registerToken(request);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PostMapping("/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@Valid @RequestBody DeactivatePushTokenRequest request) {
        pushTokenService.deactivateToken(request);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
