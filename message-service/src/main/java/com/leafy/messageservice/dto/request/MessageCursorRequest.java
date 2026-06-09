package com.leafy.messageservice.dto.request;

public record MessageCursorRequest(
    String cursor,
    Integer limit,
    String direction, // "OLDER" or "NEWER"
    String aroundMessageId
) {}
