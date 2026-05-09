package com.leafy.messageservice.dto.response;

import com.leafy.messageservice.model.enums.MessageStatus;
import com.leafy.messageservice.model.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.Map;

@Builder
public record LastMessageResponse(
        String id,
        String senderId,
        String senderName,
        String content,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "GMT+7")
        OffsetDateTime timestamp,
        MessageType type,
        MessageStatus status,
        boolean isFromMe,
        Map<String, Object> metadata
) {
}
