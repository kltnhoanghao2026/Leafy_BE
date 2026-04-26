package com.leafy.socketservice.dto;

import com.leafy.common.enums.Status;

/**
 * Pushed to online friends when a user connects or disconnects.
 */
public record PresenceEvent(
        String userId,
        Status status) {
}
