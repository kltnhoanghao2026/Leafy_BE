package com.leafy.notificationservice.service;

import com.leafy.notificationservice.document.PushTokenDocument;
import com.leafy.notificationservice.dto.DeactivatePushTokenRequest;
import com.leafy.notificationservice.dto.RegisterPushTokenRequest;
import com.leafy.notificationservice.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;

    public void registerToken(RegisterPushTokenRequest request) {
        LocalDateTime now = LocalDateTime.now();

        PushTokenDocument token = pushTokenRepository.findByFcmToken(request.getFcmToken())
                .orElse(PushTokenDocument.builder()
                        .createdAt(now)
                        .build());

        token.setUserId(request.getUserId());
        token.setPlatform(request.getPlatform());
        token.setDeviceIdentifier(request.getDeviceIdentifier());
        token.setFcmToken(request.getFcmToken());
        token.setActive(true);
        token.setLastSeenAt(now);
        token.setUpdatedAt(now);

        pushTokenRepository.save(token);
    }

    public void deactivateToken(DeactivatePushTokenRequest request) {
        deactivateToken(request.getFcmToken());
    }

    public void deactivateToken(String fcmToken) {
        pushTokenRepository.findByFcmToken(fcmToken)
                .ifPresent(token -> {
                    token.setActive(false);
                    token.setUpdatedAt(LocalDateTime.now());
                    pushTokenRepository.save(token);
                });
    }
}
