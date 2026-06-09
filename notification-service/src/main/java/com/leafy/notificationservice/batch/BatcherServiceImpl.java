package com.leafy.notificationservice.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.notificationservice.config.BatchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Default {@link BatcherService} backed by Redis.
 *
 * <h3>Key layout</h3>
 * <ul>
 *     <li>Lock key: {@code noti:batch:lock:{type}:{recipientId}[:{referenceId}]}
 *         — holds the value {@code "1"} with a TTL slightly larger than the
 *         flush window. Acquired via {@code SET NX EX}; success means this
 *         event is the first of a new batch and a flush must be scheduled.</li>
 *     <li>List key: {@code noti:batch:list:{type}:{recipientId}[:{referenceId}]}
 *         — Redis list of JSON-serialized {@link RawNotificationEvent}s appended
 *         via {@code RPUSH}. Drained and deleted by
 *         {@link BatchFlushServiceImpl#flush(String)}.</li>
 * </ul>
 *
 * <h3>Failure mode</h3>
 * Any Redis exception causes {@code buffer()} to return {@code false}, signalling
 * the caller to forward the event directly so no notifications are lost during
 * a Redis outage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatcherServiceImpl implements BatcherService {

    static final String LOCK_PREFIX = "noti:batch:lock:";
    static final String LIST_PREFIX = "noti:batch:list:";

    private final StringRedisTemplate stringRedisTemplate;
    private final BatchProperties batchProperties;
    private final BatchScheduler batchScheduler;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public boolean isBatchableType(NotificationType type) {
        return batchProperties.isBatchable(type);
    }

    @Override
    public boolean buffer(RawNotificationEvent event) {
        if (event == null || event.getType() == null || event.getRecipientId() == null) {
            return false;
        }
        if (!batchProperties.isEnabled()) {
            return false;
        }

        BatchProperties.TypeWindow cfg = batchProperties.getFor(event.getType());
        int windowSeconds = cfg.getWindowSeconds();
        if (windowSeconds <= 0) {
            return false;
        }

        String batchKey = buildBatchKey(event, cfg);
        String lockKey = LOCK_PREFIX + batchKey;
        String listKey = LIST_PREFIX + batchKey;

        try {
            String serialized = objectMapper.writeValueAsString(event);

            // TTL slightly > window so keys survive the flush race.
            Duration ttl = Duration.ofSeconds(windowSeconds + 5L);

            Boolean isNewBatch = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", ttl);

            if (Boolean.TRUE.equals(isNewBatch)) {
                batchScheduler.scheduleFlush(batchKey, windowSeconds);
                log.debug("[Batcher] New batch window opened: key={}, windowSeconds={}", batchKey, windowSeconds);
            }

            stringRedisTemplate.opsForList().rightPush(listKey, serialized);
            // Re-arm list TTL on every push so a slow batch never expires before flush.
            stringRedisTemplate.expire(listKey, ttl);

            log.debug("[Batcher] Buffered event: key={}, type={}, recipient={}, actor={}",
                    batchKey, event.getType(), event.getRecipientId(), event.getActorId());
            return true;
        } catch (Exception e) {
            log.error("[Batcher] Failed to buffer event (Redis error) — falling through to direct delivery: " +
                            "type={}, recipient={}",
                    event.getType(), event.getRecipientId(), e);
            return false;
        }
    }

    /**
     * Builds the canonical batch key. Format:
     * {@code "{type}:{recipientId}"} or {@code "{type}:{recipientId}:{referenceId}"}.
     */
    String buildBatchKey(RawNotificationEvent event, BatchProperties.TypeWindow cfg) {
        StringBuilder sb = new StringBuilder()
                .append(event.getType().name())
                .append(':')
                .append(event.getRecipientId());
        if (cfg.isIncludeReferenceInKey() && event.getReferenceId() != null) {
            sb.append(':').append(event.getReferenceId());
        }
        return sb.toString();
    }
}
