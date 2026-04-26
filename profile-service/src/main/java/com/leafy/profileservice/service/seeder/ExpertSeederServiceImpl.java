package com.leafy.profileservice.service.seeder;

import com.leafy.common.enums.ProfileRole;
import com.leafy.common.event.profile.ProfileEvent;
import com.leafy.common.model.kafka.EventType;
import com.leafy.common.publisher.OutboxEventPublisher;
import com.leafy.profileservice.model.Profile;
import com.leafy.profileservice.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpertSeederServiceImpl implements ExpertSeederService {

    private final ProfileRepository profileRepository;
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
        String[] avatars = {
                "https://i.pravatar.cc/150?img=11",
                "https://i.pravatar.cc/150?img=12",
                "https://i.pravatar.cc/150?img=13",
                "https://i.pravatar.cc/150?img=14",
                "https://i.pravatar.cc/150?img=15",
                "https://i.pravatar.cc/150?img=32",
                "https://i.pravatar.cc/150?img=33",
                "https://i.pravatar.cc/150?img=50"
        };

        for (int i = 0; i < existingProfiles.size(); i++) {
            Profile profile = existingProfiles.get(i);
            
            // Append suffix if not already present
            if (profile.getFullName() != null && !profile.getFullName().contains("- Chuyên gia")) {
                profile.setFullName(profile.getFullName() + " - Chuyên gia");
            }
            
            profile.setRole(ProfileRole.EXPERT);
            profile.setSpecialty(specialties[i % specialties.length]);
            
            // Only update avatar if they don't have one
            if (profile.getProfilePicture() == null || profile.getProfilePicture().isBlank()) {
                profile.setProfilePicture(avatars[i % avatars.length]);
            }
            
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
        
        log.info("Successfully updated {} existing profiles to experts", savedProfiles.size());
        return savedProfiles.size();
    }
}
