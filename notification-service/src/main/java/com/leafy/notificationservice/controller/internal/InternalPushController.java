package com.leafy.notificationservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.notificationservice.dto.request.TestPushRequest;
import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.event.ReadyToDeliverEvent;
import com.leafy.notificationservice.service.delivery.channel.ChannelDeliveryStrategy;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * Internal endpoint for ad-hoc push notification testing.
 *
 * <p>Sends a single test push via the active FCM {@link ChannelDeliveryStrategy}
 * to a device token supplied in the request body.
 */
@RestController
@RequestMapping("/internal/push")
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalPushController {

    @Qualifier("fcmDeliveryStrategy")
    ChannelDeliveryStrategy fcmDeliveryStrategy;

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<String>> test(@Valid @RequestBody TestPushRequest request) {
        ReadyToDeliverEvent event = ReadyToDeliverEvent.builder()
                .recipientId(request.getToken())   // token used as recipient hint; actual lookup done in strategy
                .title(request.getTitle())
                .body(request.getBody())
                .fcmData(Map.of("type", "TEST_PUSH"))
                .channels(Set.of(NotificationChannel.FCM))
                .build();

        fcmDeliveryStrategy.deliver(event);
        return ResponseEntity.ok(ApiResponse.success("Test push dispatched"));
    }
}
