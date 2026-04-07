package com.leafy.communityfeedservice.consumer;

import com.leafy.common.event.profile.ProfileEvent;
import com.leafy.communityfeedservice.model.ProfileSummary;
import com.leafy.communityfeedservice.repository.ProfileSummaryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileEventConsumer {

    ProfileSummaryRepository profileSummaryRepository;

    @KafkaListener(topics = "${kafka.topics.profile-events.created:profile.created}", groupId = "${spring.application.name}")
    public void handleProfileCreated(ProfileEvent event) {
        log.info("Received ProfileCreatedEvent: profileId={}", event.getProfileId());
        upsertProfileSummary(event);
    }

    @KafkaListener(topics = "${kafka.topics.profile-events.updated:profile.updated}", groupId = "${spring.application.name}")
    public void handleProfileUpdated(ProfileEvent event) {
        log.info("Received ProfileUpdatedEvent: profileId={}", event.getProfileId());
        upsertProfileSummary(event);
    }

    private void upsertProfileSummary(ProfileEvent event) {
        ProfileSummary summary = profileSummaryRepository.findById(event.getProfileId())
                .orElse(ProfileSummary.builder().id(event.getProfileId()).build());

        summary.setFullName(event.getFullName());
        summary.setAvatar(event.getAvatar());
        summary.setRole(event.getRole());
        summary.setIsVerified(event.getIsVerified());
        summary.setLastSyncedAt(LocalDateTime.now());

        profileSummaryRepository.save(summary);
        log.info("ProfileSummary upserted: profileId={}", event.getProfileId());
    }
}
