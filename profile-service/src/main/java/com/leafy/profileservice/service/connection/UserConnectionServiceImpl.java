package com.leafy.profileservice.service.connection;

import com.leafy.common.enums.NotificationType;
import com.leafy.common.enums.ProfileRole;
import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.common.event.profile.UserConnectionEvent;
import com.leafy.common.publisher.RawNotificationEventPublisher;
import com.leafy.profileservice.dto.response.profile.UserConnectionResponse;
import com.leafy.profileservice.model.Profile;
import com.leafy.profileservice.model.UserPreference;
import com.leafy.profileservice.model.UserConnection;
import com.leafy.profileservice.model.enums.ConsultationStatus;
import com.leafy.profileservice.model.enums.ConsultingDataType;
import com.leafy.profileservice.repository.ProfileRepository;
import com.leafy.profileservice.repository.UserConnectionRepository;
import com.leafy.profileservice.service.stream.ProfileEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.leafy.profileservice.dto.response.profile.ConsultationRequestResponse;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserConnectionServiceImpl implements UserConnectionService {

    private final UserConnectionRepository userConnectionRepository;
    private final ProfileRepository profileRepository;
    private final ProfileEventPublisher eventPublisher;
    private final com.leafy.profileservice.mapper.ProfileMapper profileMapper;
    private final RawNotificationEventPublisher notificationPublisher;

    // ── Follow / Unfollow ────────────────────────────────────────────────────

    @Override
    public UserConnectionResponse followUser(String followerProfileId, String followingProfileId) {
        if (followerProfileId.equals(followingProfileId)) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }

        UserConnection connection = userConnectionRepository
                .findByFollowerIdAndFollowingId(followerProfileId, followingProfileId)
                .orElseGet(() -> {
                    UserConnection newConn = new UserConnection();
                    newConn.setFollowerId(followerProfileId);
                    newConn.setFollowingId(followingProfileId);
                    newConn.setIsFollowing(false);
                    newConn.setConsultationStatus(ConsultationStatus.NONE);
                    return newConn;
                });

        if (!connection.getIsFollowing()) {
            connection.setIsFollowing(true);
            connection = userConnectionRepository.save(connection);

            // Publish domain event (for community-feed-service feed sync etc.)
            eventPublisher.publishUserConnectionEvent(UserConnectionEvent.builder()
                    .followerId(followerProfileId)
                    .followingId(followingProfileId)
                    .action("FOLLOW")
                    .timestamp(Instant.now().toEpochMilli())
                    .build());

            // Publish raw notification — notify the followed user
            publishFollowNotification(followerProfileId, followingProfileId);
        }

        return toResponse(connection);
    }

    @Override
    public void unfollowUser(String followerProfileId, String followingProfileId) {
        userConnectionRepository.findByFollowerIdAndFollowingId(followerProfileId, followingProfileId)
                .ifPresent(connection -> {
                    if (connection.getIsFollowing()) {
                        connection.setIsFollowing(false);
                        userConnectionRepository.save(connection);

                        eventPublisher.publishUserConnectionEvent(UserConnectionEvent.builder()
                                .followerId(followerProfileId)
                                .followingId(followingProfileId)
                                .action("UNFOLLOW")
                                .timestamp(Instant.now().toEpochMilli())
                                .build());
                    }
                });
    }

    // ── Consultation ─────────────────────────────────────────────────────────

    @Override
    public UserConnectionResponse requestConsultation(String farmerProfileId, String expertProfileId) {
        if (farmerProfileId.equals(expertProfileId)) {
            throw new IllegalArgumentException("Cannot consult yourself");
        }

        // Validate expert role using profile ID directly
        Profile expertProfile = profileRepository.findById(expertProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Expert profile not found"));

        if (expertProfile.getRole() != ProfileRole.EXPERT) {
            throw new IllegalArgumentException("Target user is not an EXPERT");
        }

        UserConnection connection = userConnectionRepository
                .findByFollowerIdAndFollowingId(farmerProfileId, expertProfileId)
                .orElseGet(() -> {
                    UserConnection newConn = new UserConnection();
                    newConn.setFollowerId(farmerProfileId);
                    newConn.setFollowingId(expertProfileId);
                    newConn.setIsFollowing(false);
                    return newConn;
                });

        connection.setConsultationStatus(ConsultationStatus.PENDING);
        connection = userConnectionRepository.save(connection);

        // Notify expert: farmer sent a consultation request
        publishConsultNotification(farmerProfileId, expertProfileId, "REQUESTED");

        return toResponse(connection);
    }

    @Override
    public void cancelConsultationRequest(String farmerProfileId, String expertProfileId) {
        userConnectionRepository.findByFollowerIdAndFollowingId(farmerProfileId, expertProfileId)
                .ifPresent(connection -> {
                    if (connection.getConsultationStatus() == ConsultationStatus.PENDING) {
                        connection.setConsultationStatus(ConsultationStatus.NONE);
                        userConnectionRepository.save(connection);
                    }
                });
    }

    @Override
    public UserConnectionResponse respondToConsultationRequest(String expertProfileId, String farmerProfileId, boolean accept) {
        UserConnection connection = userConnectionRepository
                .findByFollowerIdAndFollowingId(farmerProfileId, expertProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Consultation request not found"));

        if (connection.getConsultationStatus() != ConsultationStatus.PENDING) {
            throw new IllegalStateException("Consultation request is not in PENDING state");
        }

        connection.setConsultationStatus(accept ? ConsultationStatus.ACCEPTED : ConsultationStatus.REJECTED);
        connection = userConnectionRepository.save(connection);

        // Notify farmer only when request is ACCEPTED (not on rejection)
        if (accept) {
            publishConsultNotification(expertProfileId, farmerProfileId, "ACCEPTED");
        }

        return toResponse(connection);
    }

    // ── Query methods ────────────────────────────────────────────────────────

    @Override
    public List<String> getFollowingUsers(String followerProfileId) {
        return userConnectionRepository
                .findAllByFollowerIdAndIsFollowingTrue(followerProfileId, Pageable.unpaged())
                .stream()
                .map(UserConnection::getFollowingId)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getUserFollowers(String followingProfileId) {
        return userConnectionRepository
                .findAllByFollowingIdAndIsFollowingTrue(followingProfileId, Pageable.unpaged())
                .stream()
                .map(UserConnection::getFollowerId)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ConsultationRequestResponse> getPendingConsultations(String expertProfileId, Pageable pageable) {
        return userConnectionRepository
                .findAllByFollowingIdAndConsultationStatus(expertProfileId, ConsultationStatus.PENDING, pageable)
                .map(connection -> {
                    Profile followerProfile = profileRepository.findById(connection.getFollowerId()).orElse(null);
                    return buildConsultationResponse(connection, followerProfile);
                });
    }

    @Override
    public Page<ConsultationRequestResponse> getAcceptedConsultations(String expertProfileId, Pageable pageable) {
        return userConnectionRepository
                .findAllByFollowingIdAndConsultationStatus(expertProfileId, ConsultationStatus.ACCEPTED, pageable)
                .map(connection -> {
                    Profile followerProfile = profileRepository.findById(connection.getFollowerId()).orElse(null);
                    return buildConsultationResponse(connection, followerProfile);
                });
    }

    @Override
    public Page<ProfileResponse> getUserFollowerProfiles(String followingProfileId, Pageable pageable) {
        return userConnectionRepository
                .findAllByFollowingIdAndIsFollowingTrue(followingProfileId, pageable)
                .map(connection -> {
                    Profile followerProfile = profileRepository.findById(connection.getFollowerId()).orElse(null);
                    if (followerProfile == null) return null;
                    return profileMapper.toResponse(followerProfile);
                });
    }

    @Override
    public boolean isActiveConsultation(String expertProfileId, String farmerProfileId) {
        Profile expertProfile = profileRepository.findById(expertProfileId).orElse(null);
        if (expertProfile == null || expertProfile.getRole() != ProfileRole.EXPERT) {
            return false;
        }
        return userConnectionRepository.findByFollowerIdAndFollowingId(farmerProfileId, expertProfileId)
                .map(conn -> conn.getConsultationStatus() == ConsultationStatus.ACCEPTED)
                .orElse(false);
    }

    @Override
    public List<String> getConsultingFarmerIds(String expertProfileId) {
        return userConnectionRepository
                .findByFollowingIdAndConsultationStatus(expertProfileId, ConsultationStatus.ACCEPTED)
                .stream()
                .map(UserConnection::getFollowerId)
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasDataAccessForConsulting(String expertProfileId, String farmerProfileId, ConsultingDataType dataType) {
        if (!isActiveConsultation(expertProfileId, farmerProfileId)) {
            return false;
        }

        Profile farmerProfile = profileRepository.findById(farmerProfileId).orElse(null);
        if (farmerProfile == null || farmerProfile.getUserPreference() == null) {
            return false;
        }

        UserPreference.PrivacySettings privacy = farmerProfile.getUserPreference().getPrivacySettings();
        if (privacy == null) {
            return false;
        }

        boolean toggleEnabled = switch (dataType) {
            case FARM_PLOTS -> privacy.isShareFarmPlotsWithConsultants();
            case PLANTS -> privacy.isSharePlantsWithConsultants();
            case PLANT_EVENTS -> privacy.isSharePlantEventsWithConsultants();
            case PLANS -> privacy.isSharePlansWithConsultants();
        };

        // Farmer's privacy toggle takes precedence - if false, deny access regardless of any access requests
        return toggleEnabled;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private UserConnectionResponse toResponse(UserConnection connection) {
        return UserConnectionResponse.builder()
                .id(connection.getId())
                .followerId(connection.getFollowerId())
                .followingId(connection.getFollowingId())
                .isFollowing(connection.getIsFollowing())
                .consultationStatus(connection.getConsultationStatus())
                .createdAt(connection.getCreatedAt())
                .build();
    }

    private ConsultationRequestResponse buildConsultationResponse(UserConnection connection, Profile followerProfile) {
        return ConsultationRequestResponse.builder()
                .connectionId(connection.getId())
                .followerId(connection.getFollowerId())
                .followerName(followerProfile != null ? followerProfile.getFullName() : "Người dùng ẩn danh")
                .followerAvatar(followerProfile != null
                        ? (followerProfile.getProfilePicture() != null
                                ? followerProfile.getProfilePicture()
                                : followerProfile.getAvatar())
                        : null)
                .followerRole(followerProfile != null && followerProfile.getRole() != null
                        ? followerProfile.getRole().name()
                        : "FARMER")
                .requestedAt(connection.getCreatedAt())
                .status(connection.getConsultationStatus())
                .build();
    }

    /**
     * Publishes a USER_FOLLOW raw notification.
     * Actor info (name, avatar) is resolved from the Profile entity.
     */
    private void publishFollowNotification(String actorProfileId, String recipientProfileId) {
        try {
            Profile actor = profileRepository.findById(actorProfileId).orElse(null);
            notificationPublisher.publish(RawNotificationEvent.builder()
                    .recipientId(recipientProfileId)
                    .actorId(actorProfileId)
                    .actorName(actor != null ? actor.getFullName() : actorProfileId)
                    .actorAvatar(actor != null
                            ? (actor.getProfilePicture() != null ? actor.getProfilePicture() : actor.getAvatar())
                            : null)
                    .type(NotificationType.USER_FOLLOW)
                    .referenceId(actorProfileId)
                    .occurredAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[Notification] Failed to publish USER_FOLLOW: actor={}, recipient={}",
                    actorProfileId, recipientProfileId, e);
        }
    }

    /**
     * Publishes a CONSULT_REQUEST raw notification.
     *
     * @param actorProfileId     the profile that performed the action
     * @param recipientProfileId the profile that should receive the notification
     * @param action             "REQUESTED" or "ACCEPTED"
     */
    private void publishConsultNotification(String actorProfileId, String recipientProfileId, String action) {
        try {
            Profile actor = profileRepository.findById(actorProfileId).orElse(null);
            notificationPublisher.publish(RawNotificationEvent.builder()
                    .recipientId(recipientProfileId)
                    .actorId(actorProfileId)
                    .actorName(actor != null ? actor.getFullName() : actorProfileId)
                    .actorAvatar(actor != null
                            ? (actor.getProfilePicture() != null ? actor.getProfilePicture() : actor.getAvatar())
                            : null)
                    .type(NotificationType.CONSULT_REQUEST)
                    .referenceId(actorProfileId)
                    .payload(Map.of(
                            "isRequest", "REQUESTED".equals(action),
                            "isAccept", "ACCEPTED".equals(action)
                    ))
                    .occurredAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[Notification] Failed to publish CONSULT_REQUEST ({}): actor={}, recipient={}",
                    action, actorProfileId, recipientProfileId, e);
        }
    }
}
