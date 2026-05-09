package com.leafy.notificationservice.service.token;

import com.leafy.notificationservice.model.TokenDevice;
import com.leafy.notificationservice.dto.request.DeactivatePushTokenRequest;
import com.leafy.notificationservice.dto.request.RegisterPushTokenRequest;
import com.leafy.notificationservice.repository.PushTokenRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PushTokenServiceImpl implements PushTokenService {

    PushTokenRepository pushTokenRepository;

    @Override
    public void registerToken(RegisterPushTokenRequest request) {
        LocalDateTime now = LocalDateTime.now();

        TokenDevice token = pushTokenRepository.findByFcmToken(request.getFcmToken())
                .orElse(TokenDevice.builder()
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

    @Override
    public void deactivateToken(DeactivatePushTokenRequest request) {
        deactivateToken(request.getFcmToken());
    }

    @Override
    public void deactivateToken(String fcmToken) {
        pushTokenRepository.findByFcmToken(fcmToken)
                .ifPresent(token -> {
                    token.setActive(false);
                    token.setUpdatedAt(LocalDateTime.now());
                    pushTokenRepository.save(token);
                });
    }
}
