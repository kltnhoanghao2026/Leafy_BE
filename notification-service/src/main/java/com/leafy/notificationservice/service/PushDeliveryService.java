package com.leafy.notificationservice.service;

import java.util.Map;

public interface PushDeliveryService {
    String sendToToken(String token, String title, String body, Map<String, String> data) throws PushDeliveryException;
}
