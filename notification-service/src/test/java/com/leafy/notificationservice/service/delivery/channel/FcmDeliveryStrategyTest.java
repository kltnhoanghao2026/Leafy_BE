package com.leafy.notificationservice.service.delivery.channel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.leafy.notificationservice.enums.Platform;
import com.leafy.notificationservice.event.ReadyToDeliverEvent;
import com.leafy.notificationservice.model.TokenDevice;
import com.leafy.notificationservice.repository.PushTokenRepository;
import com.leafy.notificationservice.service.token.PushTokenService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcmDeliveryStrategyTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private PushTokenService pushTokenService;

    @Test
    void deliverTargetsOnlyRequestedWebPlatformTokens() throws Exception {
        FcmDeliveryStrategy strategy = new FcmDeliveryStrategy(firebaseMessaging, pushTokenRepository, pushTokenService);
        when(pushTokenRepository.findByUserIdAndActiveTrue("auth-user-1"))
                .thenReturn(List.of(
                        token("web-token", Platform.WEB),
                        token("android-token", Platform.ANDROID),
                        token("ios-token", Platform.IOS)
                ));
        when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id");

        strategy.deliver(ReadyToDeliverEvent.builder()
                .recipientId("profile-1")
                .recipientUserId("auth-user-1")
                .title("IoT alert")
                .body("AIR_TEMP exceeded max threshold")
                .fcmData(Map.of(
                        "type", "IOT_ALERT",
                        "alertEventId", "alert-1",
                        "referenceId", "alert-1",
                        "url", "/dashboard/alerts?alertId=alert-1"
                ))
                .fcmPlatforms(Set.of(Platform.WEB))
                .build());

        verify(firebaseMessaging).send(any(Message.class));
    }

    @Test
    void deliverSkipsWhenNoTokenMatchesRequestedPlatform() throws Exception {
        FcmDeliveryStrategy strategy = new FcmDeliveryStrategy(firebaseMessaging, pushTokenRepository, pushTokenService);
        when(pushTokenRepository.findByUserIdAndActiveTrue("auth-user-1"))
                .thenReturn(List.of(token("android-token", Platform.ANDROID)));

        strategy.deliver(ReadyToDeliverEvent.builder()
                .recipientId("profile-1")
                .recipientUserId("auth-user-1")
                .title("IoT alert")
                .body("AIR_TEMP exceeded max threshold")
                .fcmData(Map.of("type", "IOT_ALERT"))
                .fcmPlatforms(Set.of(Platform.WEB))
                .build());

        verify(firebaseMessaging, never()).send(any(Message.class));
    }

    private TokenDevice token(String fcmToken, Platform platform) {
        return TokenDevice.builder()
                .id(fcmToken + "-id")
                .userId("auth-user-1")
                .platform(platform)
                .fcmToken(fcmToken)
                .active(true)
                .build();
    }
}
