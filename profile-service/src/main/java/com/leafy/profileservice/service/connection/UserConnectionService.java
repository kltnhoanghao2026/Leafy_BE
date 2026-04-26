package com.leafy.profileservice.service.connection;

import com.leafy.profileservice.model.UserConnection;

import java.util.List;

public interface UserConnectionService {

    UserConnection followUser(String followerId, String followingId);

    void unfollowUser(String followerId, String followingId);

    UserConnection requestConsultation(String farmerId, String expertId);

    void cancelConsultationRequest(String farmerId, String expertId);

    UserConnection respondToConsultationRequest(String expertId, String farmerId, boolean accept);

    List<String> getFollowingUsers(String followerId);

    List<String> getUserFollowers(String followingId);

    org.springframework.data.domain.Page<com.leafy.profileservice.dto.response.profile.ConsultationRequestResponse> getPendingConsultations(String expertId, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<com.leafy.profileservice.dto.response.profile.ConsultationRequestResponse> getAcceptedConsultations(String expertId, org.springframework.data.domain.Pageable pageable);
}
