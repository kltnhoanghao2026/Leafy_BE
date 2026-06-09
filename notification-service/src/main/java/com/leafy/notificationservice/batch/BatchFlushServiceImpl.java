package com.leafy.notificationservice.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leafy.common.event.notification.BatchedNotificationEvent;
import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.notificationservice.publisher.NotificationReadyPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Default {@link BatchFlushService} — drains a Redis-backed batch and
 * publishes the aggregated {@link BatchedNotificationEvent} to the internal
 * {@code notification.ready} Kafka topic.
 *
 * <p>Aggregation rules (matching CNM):
 * <ul>
 *     <li>Events are sorted by {@code occurredAt} ascending so the chronologically
 *         last event becomes the {@code lastActor}.</li>
 *     <li>Actor IDs are deduplicated via a {@link LinkedHashSet}, then reversed
 *         so the most-recent actor is at index 0.</li>
 *     <li>{@code mergedPayload} is a shallow merge of all per-event payloads —
 *         later events overwrite earlier ones (last-write wins).</li>
 *     <li>{@code othersCount = max(0, actorCount - 1)} for direct template use.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchFlushServiceImpl implements BatchFlushService {

    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationReadyPublisher notificationReadyPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final TypeReference<RawNotificationEvent> EVENT_TYPE = new TypeReference<>() {};

    @Override
    public void flush(String batchKey) {
        String lockKey = BatcherServiceImpl.LOCK_PREFIX + batchKey;
        String listKey = BatcherServiceImpl.LIST_PREFIX + batchKey;

        try {
            List<String> serialized = stringRedisTemplate.opsForList().range(listKey, 0, -1);
            if (serialized == null || serialized.isEmpty()) {
                log.debug("[BatchFlush] Empty batch — skipping: key={}", batchKey);
                return;
            }

            List<RawNotificationEvent> events = new ArrayList<>(serialized.size());
            for (String json : serialized) {
                try {
                    events.add(objectMapper.readValue(json, EVENT_TYPE));
                } catch (Exception e) {
                    log.warn("[BatchFlush] Skipping un-deserializable event in batch: key={}, json={}", batchKey, json, e);
                }
            }
            if (events.isEmpty()) {
                log.warn("[BatchFlush] Batch has only undeserializable entries: key={}", batchKey);
                return;
            }

            // Chronological order — last event = most recent actor
            events.sort(Comparator.comparing(
                    RawNotificationEvent::getOccurredAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())));

            BatchedNotificationEvent batched = aggregate(events);
            notificationReadyPublisher.publishBatched(batched);

            log.info("[BatchFlush] Flushed batch: key={}, eventCount={}, actorCount={}, type={}, recipient={}",
                    batchKey, batched.getTotalEventCount(), batched.getActorCount(),
                    batched.getType(), batched.getRecipientId());
        } catch (Exception e) {
            log.error("[BatchFlush] Failed to flush batch: key={}", batchKey, e);
        } finally {
            try {
                stringRedisTemplate.delete(listKey);
                stringRedisTemplate.delete(lockKey);
            } catch (Exception cleanupErr) {
                log.warn("[BatchFlush] Failed to cleanup Redis keys for batch={}: {}", batchKey, cleanupErr.getMessage());
            }
        }
    }

    /** Build a {@link BatchedNotificationEvent} from the chronologically-sorted raw events. */
    BatchedNotificationEvent aggregate(List<RawNotificationEvent> events) {
        RawNotificationEvent last = events.get(events.size() - 1);

        // Walk events in reverse order so the LinkedHashSet is ordered by recency.
        LinkedHashSet<String> actorOrder = new LinkedHashSet<>();
        Map<String, RawNotificationEvent> latestByActor = new HashMap<>();
        for (int i = events.size() - 1; i >= 0; i--) {
            RawNotificationEvent ev = events.get(i);
            String actorId = ev.getActorId();
            if (actorId == null) continue;
            if (actorOrder.add(actorId)) {
                latestByActor.put(actorId, ev);
            }
        }

        List<String> actorIds = new ArrayList<>(actorOrder);
        int actorCount = actorIds.size();
        int othersCount = Math.max(0, actorCount - 1);

        // Second-most-recent distinct actor for "X, Y and N others" rendering.
        String secondActorId = null;
        String secondActorName = null;
        if (actorCount >= 2) {
            secondActorId = actorIds.get(1);
            RawNotificationEvent secondEv = latestByActor.get(secondActorId);
            if (secondEv != null) {
                secondActorName = secondEv.getActorName();
            }
        }

        // Shallow-merge payloads + collect raw payload list.
        Map<String, Object> merged = new HashMap<>();
        List<Map<String, Object>> rawPayloads = new ArrayList<>(events.size());
        for (RawNotificationEvent ev : events) {
            Map<String, Object> p = ev.getPayload();
            rawPayloads.add(p == null ? Collections.emptyMap() : new HashMap<>(p));
            if (p != null) {
                merged.putAll(p);
            }
        }
        // Inject standard fields used by templates.
        merged.put("actorId", last.getActorId());
        merged.put("actorName", last.getActorName());
        merged.put("actorAvatar", last.getActorAvatar());
        merged.put("referenceId", last.getReferenceId());
        merged.put("type", last.getType() != null ? last.getType().name() : null);
        merged.put("actorCount", actorCount);
        merged.put("othersCount", othersCount);
        merged.put("totalEventCount", events.size());
        if (secondActorName != null) {
            merged.put("secondActorName", secondActorName);
            merged.put("secondActorId", secondActorId);
        }

        return BatchedNotificationEvent.builder()
                .recipientId(last.getRecipientId())
                .recipientEmail(last.getRecipientEmail())
                .type(last.getType())
                .referenceId(last.getReferenceId())
                .actorIds(actorIds)
                .actorCount(actorCount)
                .totalEventCount(events.size())
                .lastActorId(last.getActorId())
                .lastActorName(last.getActorName())
                .lastActorAvatar(last.getActorAvatar())
                .secondActorId(secondActorId)
                .secondActorName(secondActorName)
                .othersCount(othersCount)
                .mergedPayload(merged)
                .rawPayloads(rawPayloads)
                .lastOccurredAt(last.getOccurredAt())
                .batchedAt(LocalDateTime.now())
                .build();
    }
}
