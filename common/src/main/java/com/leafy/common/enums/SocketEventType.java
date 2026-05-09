package com.leafy.common.enums;

/**
 * Types of real-time socket events pushed from backend services to connected clients.
 */
public enum SocketEventType {
    MESSAGE,
    CONVERSATION,
    PRESENCE,
    NOTIFICATION,
    CALL_SIGNAL,
    FORCE_LOGOUT
}
