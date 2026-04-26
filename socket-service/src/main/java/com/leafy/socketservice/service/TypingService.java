package com.leafy.socketservice.service;

import com.leafy.socketservice.dto.TypingPayload;

public interface TypingService {
    void broadcast(TypingPayload payload, String senderId);
}
