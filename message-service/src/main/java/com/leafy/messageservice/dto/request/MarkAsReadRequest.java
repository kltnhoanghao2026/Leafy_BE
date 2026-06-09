package com.leafy.messageservice.dto.request;

public record MarkAsReadRequest(
        String lastReadMessageId
) {}
