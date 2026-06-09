package com.leafy.common.dto.client.socketservice;

import com.leafy.common.enums.SocketEventType;

/**
 * Kafka payload published by any service that needs to push a real-time event
 * to a WebSocket-connected client via socket-service.
 *
 * @param type         event classification
 * @param targetUserId the userId to route to (null = broadcast)
 * @param destination  STOMP destination e.g. /queue/messages, /topic/group.{id}
 * @param payload      the actual data to serialise and send
 */
public record SocketEvent(
        SocketEventType type,
        String targetUserId,
        String destination,
        Object payload
) {}
