package com.leafy.searchservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.common.event.profile.ProfileUpsertEvent;
import com.leafy.common.model.kafka.EventType;
import com.leafy.searchservice.client.AuthUserClient;
import com.leafy.searchservice.client.ProfileClient;
import com.leafy.searchservice.client.dto.AuthUserResponse;
import com.leafy.searchservice.client.dto.ProfileServiceProfileResponse;
import com.leafy.searchservice.config.ElasticSearchProperties;
import com.leafy.searchservice.model.elasticsearch.ProfileIndex;
import com.leafy.searchservice.services.failedevent.FailedEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
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
public class ProfileIndexUpsertListenner {

    private final FailedEventService failedEventService;
    private final ElasticsearchOperations elasOps;
    private final ElasticSearchProperties elasProps;
    private final ObjectMapper objectMapper;
    private final ProfileClient profileClient;
    private final AuthUserClient authUserClient;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".dlq",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "#{kafkaTopicProperties.profileEvents.created}",
            groupId = "search-service-indexer-group",
            concurrency = "3"
    )
    public void handleProfileUpsert(
            @Payload ProfileUpsertEvent profileUpsertEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        String profileId = profileUpsertEvent.getProfileId();
        log.info("Processing profile index request: profileId={}, partition={}, offset={}",
                profileId, partition, offset);


        try {
            ProfileIndex profileIndex = toProfileIndex(profileId);
            elasOps.save(profileIndex, IndexCoordinates.of(elasProps.getProfileAlias()));

            log.info("Profile indexed successfully: profileId={}", profileId);

        } catch (Exception e) {
            log.error("Failed to index profile: profileId={}", profileId, e);
            throw e;
        }
    }

    @DltHandler
    public void handleProfileIndexUpsertDlq(
            @Payload ProfileUpsertEvent profileUpsertEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String dlqTopic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int dlqPartition,
            @Header(KafkaHeaders.OFFSET) long dlqOffset,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) byte[] errorMsgBytes,
            @Header(value = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) byte[] stackTraceBytes,
            @Header(value = "kafka_dlt-original-topic", required = false) String originalTopic,
            @Header(value = "kafka_dlt-original-partition", required = false) Integer originalPartition,
            @Header(value = "kafka_dlt-original-offset", required = false) Long originalOffset,
            @Header(value = "retry_topic-attempts", required = false) byte[] attemptsBytes) {
        String errorMessage = errorMsgBytes != null ? new String(errorMsgBytes) : "Unknown error";
        String stackTrace = stackTraceBytes != null ? new String(stackTraceBytes) : "No stacktrace available";

        // Final topic info to save
        String finalTopic = (originalTopic != null) ? originalTopic : dlqTopic;
        int finalPartition = (originalPartition != null) ? originalPartition : dlqPartition;
        long finalOffset = (originalOffset != null) ? originalOffset : dlqOffset;

        // Parse attempts
        int retryCount = 0;
        if (attemptsBytes != null) {
            try {
                retryCount = ByteBuffer.wrap(attemptsBytes).getInt();
            } catch (Exception ignored) {
            }
        }

        log.error("Index requested event moved to DLQ: profileId={}, originalTopic={}, error={}",
                profileUpsertEvent.getProfileId(), finalTopic, errorMessage);

        try {
            String payloadJson = objectMapper.writeValueAsString(profileUpsertEvent);
            failedEventService.logFailure(profileUpsertEvent.getProfileId(), EventType.PROFILE_CREATED, finalTopic, finalPartition, finalOffset, payloadJson, errorMessage, stackTrace, retryCount);
        } catch (Exception ex) {
            log.error("Critical error while logging failure to MongoDB", ex);
        }
    }

    private ProfileIndex toProfileIndex(String profileId) {
        ProfileServiceProfileResponse profile = getProfile(profileId);
        AuthUserResponse user = getUser(profile.getUserId());

        return ProfileIndex.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .fullName(profile.getFullName())
            .profilePicture(profile.getProfilePicture())
            .avatar(profile.getAvatar())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .role(profile.getRole())
                .specialty(profile.getSpecialty())
                .isVerified(profile.getIsVerified())
                .active(profile.getActive())
                .bio(profile.getBio())
                .build();
    }

    private ProfileServiceProfileResponse getProfile(String profileId) {
        var response = profileClient.getProfileById(profileId);

        if (response == null || response.data() == null) {
            throw new IllegalStateException("Profile service returned empty data for profileId=" + profileId);
        }

        return response.data();
    }

    private AuthUserResponse getUser(String userId) {
        var response = authUserClient.getUserById(userId);

        if (response == null || response.data() == null) {
            throw new IllegalStateException("Auth service returned empty data for userId=" + userId);
        }

        return response.data();
    }

}
