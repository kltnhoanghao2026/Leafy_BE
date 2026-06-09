package com.leafy.notificationservice.config;

import com.leafy.common.enums.NotificationType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the Redis-backed batching layer.
 *
 * <p>Each {@link NotificationType} is mapped to a {@link TypeWindow} controlling
 * how raw events for that type are aggregated:
 * <ul>
 *     <li>{@code windowSeconds = 0} → non-batchable, events flow straight through
 *         (still wrapped as a one-element batch by the consumer to keep the
 *         delivery code path uniform).</li>
 *     <li>{@code windowSeconds &gt; 0} → events are buffered in Redis for that many
 *         seconds keyed by {@code (type, recipientId)} and (when
 *         {@code includeReferenceInKey=true}) {@code referenceId}.</li>
 * </ul>
 *
 * <p>Defaults are sensible for KLTN's social interactions; override in
 * {@code application.yaml} or the config-server under {@code notification.batch.types.*}.
 */
@Component
@ConfigurationProperties(prefix = "notification.batch")
@Getter
@Setter
public class BatchProperties {

    /** Master switch — when {@code false}, every event is treated as non-batchable. */
    private boolean enabled = true;

    /** Window applied to types that don't appear in {@link #types}. */
    private int defaultWindowSeconds = 30;

    /** Spring TaskScheduler thread-pool size used by the batching layer. */
    private int schedulerPoolSize = 4;

    /** Thread-name prefix for the batching TaskScheduler. */
    private String schedulerThreadPrefix = "noti-batch-flush-";

    /**
     * Per-type window configuration. Bound from
     * {@code notification.batch.types.<TYPE_NAME>.window-seconds} etc.
     */
    private Map<NotificationType, TypeWindow> types = new HashMap<>();

    /**
     * Resolves the batching window for a given notification type, falling back
     * to {@link #defaultWindowSeconds} when no explicit entry exists.
     */
    public TypeWindow getFor(NotificationType type) {
        if (type == null) {
            return new TypeWindow(0, false);
        }
        TypeWindow cfg = types.get(type);
        if (cfg != null) {
            return cfg;
        }
        return new TypeWindow(defaultWindowSeconds, false);
    }

    /** Convenience — {@code true} when the type has a positive window and the layer is enabled. */
    public boolean isBatchable(NotificationType type) {
        if (!enabled) return false;
        return getFor(type).getWindowSeconds() > 0;
    }

    @Getter
    @Setter
    public static class TypeWindow {
        /** Buffer duration in seconds. {@code 0} = non-batchable. */
        private int windowSeconds;
        /** When {@code true}, the batch key includes {@code referenceId}. */
        private boolean includeReferenceInKey;

        public TypeWindow() {}

        public TypeWindow(int windowSeconds, boolean includeReferenceInKey) {
            this.windowSeconds = windowSeconds;
            this.includeReferenceInKey = includeReferenceInKey;
        }
    }
}
