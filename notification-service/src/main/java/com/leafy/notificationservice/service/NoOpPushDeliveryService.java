package com.leafy.notificationservice.service;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnMissingBean(PushDeliveryService.class)
public class NoOpPushDeliveryService implements PushDeliveryService {

    @Override
    public String sendToToken(String token, String title, String body, Map<String, String> data) {
        log.info("Firebase push delivery is disabled or not configured; skipping push send");
        return "NOOP";
    }
}
