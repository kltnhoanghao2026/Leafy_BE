package com.leafy.profileservice.service.stream;

import com.leafy.common.event.profile.UserConnectionEvent;
import com.leafy.common.publisher.OutboxEventPublisher;
import com.leafy.common.model.kafka.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileEventPublisher {

    private final OutboxEventPublisher outboxEventPublisher;

    public void publishUserConnectionEvent(UserConnectionEvent event) {
        log.info("Publishing UserConnectionEvent: {}", event);
        outboxEventPublisher.saveAndPublish(
                event.getFollowerId() + "-" + event.getFollowingId(),
                "USER_CONNECTION",
                EventType.PROFILE_CONNECTION_UPDATED,
                event
        );
    }
}
