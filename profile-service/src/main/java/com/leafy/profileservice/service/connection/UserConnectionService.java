package com.leafy.profileservice.service.connection;

import com.leafy.profileservice.dto.response.profile.UserConnectionResponse;
import com.leafy.profileservice.dto.response.profile.ConsultationRequestResponse;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import com.leafy.profileservice.model.enums.ConsultingDataType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserConnectionService {

    UserConnectionResponse followUser(String followerId, String followingId);

    void unfollowUser(String followerId, String followingId);

    UserConnectionResponse requestConsultation(String farmerId, String expertId);

    void cancelConsultationRequest(String farmerId, String expertId);

    UserConnectionResponse respondToConsultationRequest(String expertId, String farmerId, boolean accept);

    List<String> getFollowingUsers(String followerId);

    List<String> getUserFollowers(String followingId);

    Page<ConsultationRequestResponse> getPendingConsultations(String expertId, Pageable pageable);

    Page<ConsultationRequestResponse> getAcceptedConsultations(String expertId, Pageable pageable);

    Page<ProfileResponse> getUserFollowerProfiles(String followingId, Pageable pageable);

    boolean isActiveConsultation(String expertProfileId, String farmerProfileId);

    List<String> getConsultingFarmerIds(String expertProfileId);

    /**
     * Checks whether an expert has access to a specific data type of a farmer.
     * Access is granted if:
     * 1. An ACCEPTED consultation exists, AND
     * 2. The farmer's sharing toggle is true.
     * 
     * Note: The farmer's privacy toggle takes precedence. Even if an access request is APPROVED,
     * access is denied if the farmer has set the sharing toggle to false.
     *
     * @param expertProfileId the expert's profile ID
     * @param farmerProfileId the farmer's profile ID
     * @param dataType the data type being accessed
     * @return true if access is granted, false otherwise
     */
    boolean hasDataAccessForConsulting(String expertProfileId, String farmerProfileId, ConsultingDataType dataType);
}
