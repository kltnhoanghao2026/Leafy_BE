package com.leafy.profileservice.service.seeder;

import com.leafy.common.enums.ProfileRole;
import com.leafy.common.event.profile.ProfileEvent;
import com.leafy.common.model.kafka.EventType;
import com.leafy.common.publisher.OutboxEventPublisher;
import com.leafy.profileservice.model.Profile;
import com.leafy.profileservice.model.UserConnection;
import com.leafy.profileservice.model.enums.ConsultationStatus;
import com.leafy.profileservice.repository.ProfileRepository;
import com.leafy.profileservice.repository.UserConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpertSeederServiceImpl implements ExpertSeederService {

    private final ProfileRepository profileRepository;
    private final UserConnectionRepository userConnectionRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Override
    public int seedExperts(int count) {
        log.info("Attempting to seed {} experts by updating existing profiles", count);

        // Fetch up to 'count' active profiles that are NOT already experts
        org.springframework.data.domain.Page<Profile> profilesPage = profileRepository.findByActiveTrue(
                org.springframework.data.domain.PageRequest.of(0, count)
        );
        
        List<Profile> existingProfiles = profilesPage.getContent();
        if (existingProfiles.isEmpty()) {
            log.warn("No existing active profiles found to convert to experts.");
            return 0;
        }

        String[] specialties = {"Trồng trọt", "Chăn nuôi", "Phân bón", "Bảo vệ thực vật", "Nông nghiệp hữu cơ", "Tưới tiêu"};

        for (int i = 0; i < existingProfiles.size(); i++) {
            Profile profile = existingProfiles.get(i);

            // Append suffix if not already present
            if (profile.getFullName() != null && !profile.getFullName().contains("- Chuyên gia")) {
                profile.setFullName(profile.getFullName() + " - Chuyên gia");
            }

            profile.setRole(ProfileRole.EXPERT);
            profile.setSpecialty(specialties[i % specialties.length]);
            profile.setIsVerified(true);
            profile.setBio("Tôi là chuyên gia có nhiều năm kinh nghiệm tư vấn và thực hành trong lĩnh vực " + specialties[i % specialties.length] + ".");
        }

        List<Profile> savedProfiles = profileRepository.saveAll(existingProfiles);
        
        // Publish events for Elasticsearch and other services to sync
        for (Profile profile : savedProfiles) {
            ProfileEvent event = ProfileEvent.builder()
                    .profileId(profile.getId())
                    .fullName(profile.getFullName())
                    .avatar(profile.getAvatar())
                    .role(profile.getRole() != null ? profile.getRole().name() : null)
                    .isVerified(profile.getIsVerified())
                    .timestamp(System.currentTimeMillis())
                    .build();
            outboxEventPublisher.saveAndPublish(profile.getId(), "PROFILE", EventType.PROFILE_UPDATED, event);
        }
        
        // Create accepted consulting UserConnections between each expert and farmer profiles
        List<String> expertIds = savedProfiles.stream().map(Profile::getId).toList();
        List<Profile> farmerProfiles = profileRepository.findByActiveTrueAndIdNotIn(
                expertIds, PageRequest.of(0, count * 3)
        );

        for (Profile expert : savedProfiles) {
            for (Profile farmer : farmerProfiles) {
                boolean exists = userConnectionRepository
                        .findByFollowerIdAndFollowingId(farmer.getId(), expert.getId())
                        .isPresent();
                if (!exists) {
                    UserConnection connection = UserConnection.builder()
                            .followerId(farmer.getId())
                            .followingId(expert.getId())
                            .isFollowing(true)
                            .consultationStatus(ConsultationStatus.ACCEPTED)
                            .build();
                    userConnectionRepository.save(connection);
                }
            }
        }
        log.info("Created consulting connections between {} experts and {} farmers",
                savedProfiles.size(), farmerProfiles.size());

        log.info("Successfully updated {} existing profiles to experts", savedProfiles.size());
        return savedProfiles.size();
    }
}
