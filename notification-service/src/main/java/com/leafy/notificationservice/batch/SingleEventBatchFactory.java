package com.leafy.notificationservice.batch;

import com.leafy.common.event.notification.BatchedNotificationEvent;
import com.leafy.common.event.notification.RawNotificationEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps a single {@link RawNotificationEvent} as a one-element
 * {@link BatchedNotificationEvent}.
 *
 * <p>Used when an event bypasses the batching layer (non-batchable type or
 * Redis outage) so the downstream delivery code path always receives a
 * uniform {@code BatchedNotificationEvent}.
 */
public final class SingleEventBatchFactory {

    private SingleEventBatchFactory() {}

    public static BatchedNotificationEvent wrap(RawNotificationEvent event) {
        Map<String, Object> merged = new HashMap<>();
        if (event.getPayload() != null) {
            merged.putAll(event.getPayload());
        }
        merged.put("actorId", event.getActorId());
        merged.put("actorName", event.getActorName());
        merged.put("actorAvatar", event.getActorAvatar());
        merged.put("referenceId", event.getReferenceId());
        merged.put("type", event.getType() != null ? event.getType().name() : null);
        merged.put("actorCount", 1);
        merged.put("othersCount", 0);
        merged.put("totalEventCount", 1);

        List<String> actorIds = event.getActorId() != null
                ? new ArrayList<>(Collections.singletonList(event.getActorId()))
                : new ArrayList<>();

        List<Map<String, Object>> rawPayloads = new ArrayList<>(1);
        rawPayloads.add(event.getPayload() == null ? Collections.emptyMap() : new HashMap<>(event.getPayload()));

        return BatchedNotificationEvent.builder()
                .recipientId(event.getRecipientId())
                .recipientEmail(event.getRecipientEmail())
                .type(event.getType())
                .referenceId(event.getReferenceId())
                .actorIds(actorIds)
                .actorCount(actorIds.size())
                .totalEventCount(1)
                .lastActorId(event.getActorId())
                .lastActorName(event.getActorName())
                .lastActorAvatar(event.getActorAvatar())
                .secondActorId(null)
                .secondActorName(null)
                .othersCount(0)
                .mergedPayload(merged)
                .rawPayloads(rawPayloads)
                .lastOccurredAt(event.getOccurredAt() != null ? event.getOccurredAt() : LocalDateTime.now())
                .batchedAt(LocalDateTime.now())
                .build();
    }
}
