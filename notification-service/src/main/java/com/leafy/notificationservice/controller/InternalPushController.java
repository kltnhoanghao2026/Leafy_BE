package com.leafy.notificationservice.controller;

import com.leafy.notificationservice.dto.TestPushRequest;
import com.leafy.notificationservice.service.PushDeliveryException;
import com.leafy.notificationservice.service.PushDeliveryService;
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

    private final PushDeliveryService pushDeliveryService;

    @PostMapping("/test")
    public ResponseEntity<String> test(@Valid @RequestBody TestPushRequest request)
            throws PushDeliveryException {

        String messageId = pushDeliveryService.sendToToken(
                request.getToken(),
                request.getTitle(),
                request.getBody(),
                Map.of("type", "TEST_PUSH")
        );

        return ResponseEntity.ok(messageId);
    }
}
