package com.leafy.searchservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.common.event.post.PostDeletedEvent;
import com.leafy.common.event.post.PostUpsertEvent;
import com.leafy.common.model.kafka.EventType;
import com.leafy.searchservice.services.failedevent.FailedEventService;
import com.leafy.searchservice.services.sync.PostIndexSyncImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostIndexEventListener {

    private final FailedEventService failedEventService;
    private final PostIndexSyncImpl postIndexSync;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".dlq",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "#{kafkaTopicProperties.postEvents.upserted}",
            groupId = "search-service-post-indexer-group",
            concurrency = "3"
    )
    public void handlePostUpsert(
            @Payload PostUpsertEvent postUpsertEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        String postId = postUpsertEvent.getPostId();
        log.info("Processing post upsert index request: postId={}, partition={}, offset={}", postId, partition, offset);

        postIndexSync.upsertPost(postId);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".dlq",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "#{kafkaTopicProperties.postEvents.deleted}",
            groupId = "search-service-post-indexer-group",
            concurrency = "3"
    )
    public void handlePostDelete(
            @Payload PostDeletedEvent postDeletedEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        String postId = postDeletedEvent.getPostId();
        log.info("Processing post delete index request: postId={}, partition={}, offset={}", postId, partition, offset);

        postIndexSync.deletePost(postId);
    }

    @DltHandler
    public void handlePostUpsertDlq(
            @Payload PostUpsertEvent postUpsertEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String dlqTopic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int dlqPartition,
            @Header(KafkaHeaders.OFFSET) long dlqOffset,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) byte[] errorMsgBytes,
            @Header(value = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) byte[] stackTraceBytes,
            @Header(value = "kafka_dlt-original-topic", required = false) String originalTopic,
            @Header(value = "kafka_dlt-original-partition", required = false) Integer originalPartition,
            @Header(value = "kafka_dlt-original-offset", required = false) Long originalOffset,
            @Header(value = "retry_topic-attempts", required = false) byte[] attemptsBytes) {
        handleDlqFailure(
                postUpsertEvent.getPostId(),
                EventType.POST_UPSERTED,
                postUpsertEvent,
                dlqTopic,
                dlqPartition,
                dlqOffset,
                errorMsgBytes,
                stackTraceBytes,
                originalTopic,
                originalPartition,
                originalOffset,
                attemptsBytes
        );
    }

    @DltHandler
    public void handlePostDeleteDlq(
            @Payload PostDeletedEvent postDeletedEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String dlqTopic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int dlqPartition,
            @Header(KafkaHeaders.OFFSET) long dlqOffset,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) byte[] errorMsgBytes,
            @Header(value = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) byte[] stackTraceBytes,
            @Header(value = "kafka_dlt-original-topic", required = false) String originalTopic,
            @Header(value = "kafka_dlt-original-partition", required = false) Integer originalPartition,
            @Header(value = "kafka_dlt-original-offset", required = false) Long originalOffset,
            @Header(value = "retry_topic-attempts", required = false) byte[] attemptsBytes) {
        handleDlqFailure(
                postDeletedEvent.getPostId(),
                EventType.POST_DELETED,
                postDeletedEvent,
                dlqTopic,
                dlqPartition,
                dlqOffset,
                errorMsgBytes,
                stackTraceBytes,
                originalTopic,
                originalPartition,
                originalOffset,
                attemptsBytes
        );
    }

    private void handleDlqFailure(
            String eventId,
            EventType eventType,
            Object payload,
            String dlqTopic,
            int dlqPartition,
            long dlqOffset,
            byte[] errorMsgBytes,
            byte[] stackTraceBytes,
            String originalTopic,
            Integer originalPartition,
            Long originalOffset,
            byte[] attemptsBytes) {
        String errorMessage = errorMsgBytes != null ? new String(errorMsgBytes) : "Unknown error";
        String stackTrace = stackTraceBytes != null ? new String(stackTraceBytes) : "No stacktrace available";

        String finalTopic = originalTopic != null ? originalTopic : dlqTopic;
        int finalPartition = originalPartition != null ? originalPartition : dlqPartition;
        long finalOffset = originalOffset != null ? originalOffset : dlqOffset;
        int retryCount = parseRetryCount(attemptsBytes);

        log.error("Post indexing event moved to DLQ: eventType={}, eventId={}, originalTopic={}, error={}",
                eventType, eventId, finalTopic, errorMessage);

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            failedEventService.logFailure(
                    eventId,
                    eventType,
                    finalTopic,
                    finalPartition,
                    finalOffset,
                    payloadJson,
                    errorMessage,
                    stackTrace,
                    retryCount
            );
        } catch (Exception exception) {
            log.error("Critical error while logging post indexing failure to MongoDB", exception);
        }
    }

    private int parseRetryCount(byte[] attemptsBytes) {
        if (attemptsBytes == null) {
            return 0;
        }

        try {
            return ByteBuffer.wrap(attemptsBytes).getInt();
        } catch (Exception ignored) {
            return 0;
        }
    }
}