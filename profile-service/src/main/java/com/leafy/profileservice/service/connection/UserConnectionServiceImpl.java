package com.leafy.profileservice.service.connection;

import com.leafy.common.enums.ProfileRole;
import com.leafy.common.event.profile.UserConnectionEvent;
import com.leafy.profileservice.model.Profile;
import com.leafy.profileservice.model.UserConnection;
import com.leafy.profileservice.model.enums.ConsultationStatus;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserConnectionServiceImpl implements UserConnectionService {

    private final UserConnectionRepository userConnectionRepository;
    private final ProfileRepository profileRepository;
    private final ProfileEventPublisher eventPublisher;
    private final com.leafy.profileservice.mapper.ProfileMapper profileMapper;

    @Override
    public UserConnection followUser(String followerId, String followingId) {
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }

        UserConnection connection = userConnectionRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .orElseGet(() -> {
                    UserConnection newConn = new UserConnection();
                    newConn.setFollowerId(followerId);
                    newConn.setFollowingId(followingId);
                    newConn.setIsFollowing(false);
                    newConn.setConsultationStatus(ConsultationStatus.NONE);
                    return newConn;
                });

        if (!connection.getIsFollowing()) {
            connection.setIsFollowing(true);
            connection = userConnectionRepository.save(connection);

            eventPublisher.publishUserConnectionEvent(UserConnectionEvent.builder()
                    .followerId(followerId)
                    .followingId(followingId)
                    .action("FOLLOW")
                    .timestamp(Instant.now().toEpochMilli())
                    .build());
        }

        return connection;
    }

    @Override
    public void unfollowUser(String followerId, String followingId) {
        userConnectionRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .ifPresent(connection -> {
                    if (connection.getIsFollowing()) {
                        connection.setIsFollowing(false);
                        userConnectionRepository.save(connection);

                        eventPublisher.publishUserConnectionEvent(UserConnectionEvent.builder()
                                .followerId(followerId)
                                .followingId(followingId)
                                .action("UNFOLLOW")
                                .timestamp(Instant.now().toEpochMilli())
                                .build());
                    }
                });
    }

    @Override
    public UserConnection requestConsultation(String farmerId, String expertId) {
        if (farmerId.equals(expertId)) {
            throw new IllegalArgumentException("Cannot consult yourself");
        }

        Profile expertProfile = profileRepository.findByUserId(expertId)
                .orElseThrow(() -> new IllegalArgumentException("Expert not found"));

        if (expertProfile.getRole() != ProfileRole.EXPERT) {
            throw new IllegalArgumentException("Target user is not an EXPERT");
        }

        UserConnection connection = userConnectionRepository.findByFollowerIdAndFollowingId(farmerId, expertId)
                .orElseGet(() -> {
                    UserConnection newConn = new UserConnection();
                    newConn.setFollowerId(farmerId);
                    newConn.setFollowingId(expertId);
                    newConn.setIsFollowing(false);
                    return newConn;
                });

        connection.setConsultationStatus(ConsultationStatus.PENDING);
        return userConnectionRepository.save(connection);
    }

    @Override
    public void cancelConsultationRequest(String farmerId, String expertId) {
        userConnectionRepository.findByFollowerIdAndFollowingId(farmerId, expertId)
                .ifPresent(connection -> {
                    if (connection.getConsultationStatus() == ConsultationStatus.PENDING) {
                        connection.setConsultationStatus(ConsultationStatus.NONE);
                        userConnectionRepository.save(connection);
                    }
                });
    }

    @Override
    public UserConnection respondToConsultationRequest(String expertId, String farmerId, boolean accept) {
        UserConnection connection = userConnectionRepository.findByFollowerIdAndFollowingId(farmerId, expertId)
                .orElseThrow(() -> new IllegalArgumentException("Consultation request not found"));

        if (connection.getConsultationStatus() != ConsultationStatus.PENDING) {
            throw new IllegalStateException("Consultation request is not in PENDING state");
        }

        connection.setConsultationStatus(accept ? ConsultationStatus.ACCEPTED : ConsultationStatus.REJECTED);
        return userConnectionRepository.save(connection);
    }

    @Override
    public List<String> getFollowingUsers(String followerId) {
        return userConnectionRepository.findAllByFollowerIdAndIsFollowingTrue(followerId, Pageable.unpaged())
                .stream()
                .map(UserConnection::getFollowingId)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getUserFollowers(String followingId) {
        return userConnectionRepository.findAllByFollowingIdAndIsFollowingTrue(followingId, Pageable.unpaged())
                .stream()
                .map(UserConnection::getFollowerId)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ConsultationRequestResponse> getPendingConsultations(String expertId, Pageable pageable) {
        return userConnectionRepository.findAllByFollowingIdAndConsultationStatus(expertId, ConsultationStatus.PENDING, pageable)
                .map(connection -> {
                    Profile followerProfile = profileRepository.findByUserId(connection.getFollowerId()).orElse(null);
                    
                    return ConsultationRequestResponse.builder()
                            .connectionId(connection.getId())
                            .followerId(connection.getFollowerId())
                            .followerName(followerProfile != null ? followerProfile.getFullName() : "Người dùng ẩn danh")
                            .followerAvatar(followerProfile != null ? (followerProfile.getProfilePicture() != null ? followerProfile.getProfilePicture() : followerProfile.getAvatar()) : null)
                            .followerRole(followerProfile != null && followerProfile.getRole() != null ? followerProfile.getRole().name() : "FARMER")
                            .requestedAt(connection.getCreatedAt())
                            .status(connection.getConsultationStatus())
                            .build();
                });
    }

    @Override
    public Page<ConsultationRequestResponse> getAcceptedConsultations(String expertId, Pageable pageable) {
        return userConnectionRepository.findAllByFollowingIdAndConsultationStatus(expertId, ConsultationStatus.ACCEPTED, pageable)
                .map(connection -> {
                    Profile followerProfile = profileRepository.findByUserId(connection.getFollowerId()).orElse(null);
                    
                    return ConsultationRequestResponse.builder()
                            .connectionId(connection.getId())
                            .followerId(connection.getFollowerId())
                            .followerName(followerProfile != null ? followerProfile.getFullName() : "Người dùng ẩn danh")
                            .followerAvatar(followerProfile != null ? (followerProfile.getProfilePicture() != null ? followerProfile.getProfilePicture() : followerProfile.getAvatar()) : null)
                            .followerRole(followerProfile != null && followerProfile.getRole() != null ? followerProfile.getRole().name() : "FARMER")
                            .requestedAt(connection.getCreatedAt())
                            .status(connection.getConsultationStatus())
                            .build();
                });
    }

    @Override
    public Page<ProfileResponse> getUserFollowerProfiles(String followingId, Pageable pageable) {
        return userConnectionRepository.findAllByFollowingIdAndIsFollowingTrue(followingId, pageable)
                .map(connection -> {
                    Profile followerProfile = profileRepository.findByUserId(connection.getFollowerId()).orElse(null);
                    if (followerProfile == null) return null;
                    return profileMapper.toResponse(followerProfile);
                });
    }
}
