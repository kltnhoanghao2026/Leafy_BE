package com.leafy.notificationservice.service.token;

public interface PushTokenService {
    void registerToken(com.leafy.notificationservice.dto.request.RegisterPushTokenRequest request);
    void deactivateToken(com.leafy.notificationservice.dto.request.DeactivatePushTokenRequest request);
    void deactivateToken(String fcmToken);
}
