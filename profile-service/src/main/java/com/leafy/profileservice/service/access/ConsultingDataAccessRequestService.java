package com.leafy.profileservice.service.access;

import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.common.publisher.RawNotificationEventPublisher;
import com.leafy.profileservice.dto.response.access.ConsultingDataAccessRequestResponse;
import com.leafy.profileservice.model.ConsultingDataAccessRequest;
import com.leafy.profileservice.model.Profile;
import com.leafy.profileservice.model.enums.AccessRequestStatus;
import com.leafy.profileservice.model.enums.ConsultingDataType;
import com.leafy.common.enums.ProfileRole;
import com.leafy.profileservice.repository.ConsultingDataAccessRequestRepository;
import com.leafy.profileservice.repository.ProfileRepository;
import com.leafy.profileservice.repository.UserConnectionRepository;
import com.leafy.profileservice.model.enums.ConsultationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultingDataAccessRequestService {

    private final ConsultingDataAccessRequestRepository accessRequestRepository;
    private final ProfileRepository profileRepository;
    private final UserConnectionRepository userConnectionRepository;
    private final RawNotificationEventPublisher notificationPublisher;

    /**
     * Expert requests access to a specific data type of a consulted farmer.
     * If a PENDING or DENIED request already exists, it is re-activated to PENDING.
     */
    public ConsultingDataAccessRequestResponse requestAccess(
            String expertProfileId,
            String farmerProfileId,
            ConsultingDataType dataType,
            String message) {

        validateExpertRole(expertProfileId);
        validateActiveConsultation(farmerProfileId, expertProfileId);

        ConsultingDataAccessRequest request = accessRequestRepository
                .findByExpertProfileIdAndFarmerProfileIdAndDataType(expertProfileId, farmerProfileId, dataType)
                .orElseGet(() -> ConsultingDataAccessRequest.builder()
                        .expertProfileId(expertProfileId)
                        .farmerProfileId(farmerProfileId)
                        .dataType(dataType)
                        .build());

        request.setStatus(AccessRequestStatus.PENDING);
        request.setExpertMessage(message);
        request = accessRequestRepository.save(request);

        log.info("Access request created/updated: expert={}, farmer={}, dataType={}",
                expertProfileId, farmerProfileId, dataType);

        publishAccessRequestNotification(expertProfileId, farmerProfileId, dataType);

        return toResponse(request);
    }

    /**
     * Farmer approves a pending access request.
     */
    public ConsultingDataAccessRequestResponse approveRequest(String requestId, String farmerProfileId) {
        ConsultingDataAccessRequest request = findAndValidateOwner(requestId, farmerProfileId);
        ensurePending(request);

        request.setStatus(AccessRequestStatus.APPROVED);
        request.setUpdatedAt(LocalDateTime.now());
        request = accessRequestRepository.save(request);

        log.info("Access request approved: requestId={}", requestId);
        publishApprovalNotification(request, true);
        return toResponse(request);
    }

    /**
     * Farmer denies a pending access request.
     */
    public ConsultingDataAccessRequestResponse denyRequest(String requestId, String farmerProfileId) {
        ConsultingDataAccessRequest request = findAndValidateOwner(requestId, farmerProfileId);
        ensurePending(request);

        request.setStatus(AccessRequestStatus.DENIED);
        request.setUpdatedAt(LocalDateTime.now());
        request = accessRequestRepository.save(request);

        log.info("Access request denied: requestId={}", requestId);
        publishApprovalNotification(request, false);
        return toResponse(request);
    }

    /**
     * Get all pending access requests for a farmer (for the farmer's notification/approval UI).
     */
    public Page<ConsultingDataAccessRequestResponse> getPendingRequestsForFarmer(
            String farmerProfileId, Pageable pageable) {
        return accessRequestRepository
                .findByFarmerProfileIdAndStatus(farmerProfileId, AccessRequestStatus.PENDING, pageable)
                .map(this::toResponse);
    }

    /**
     * Get all access requests made by an expert.
     */
    public Page<ConsultingDataAccessRequestResponse> getRequestsByExpert(
            String expertProfileId, Pageable pageable) {
        return accessRequestRepository
                .findByExpertProfileIdAndStatus(expertProfileId, AccessRequestStatus.PENDING, pageable)
                .map(this::toResponse);
    }

    /**
     * Check if an approved request exists for the given (expert, farmer, dataType) tuple.
     */
    public boolean hasApprovedAccess(String expertProfileId, String farmerProfileId, ConsultingDataType dataType) {
        return accessRequestRepository.existsByExpertProfileIdAndFarmerProfileIdAndDataTypeAndStatus(
                expertProfileId, farmerProfileId, dataType, AccessRequestStatus.APPROVED);
    }

    /**
     * Expire old pending requests (for scheduled cleanup job).
     */
    public void expireOldPendingRequests(LocalDateTime cutoff) {
        var oldRequests = accessRequestRepository
                .findByStatusAndCreatedAtBefore(AccessRequestStatus.PENDING, cutoff);
        for (var request : oldRequests) {
            request.setStatus(AccessRequestStatus.EXPIRED);
        }
        accessRequestRepository.saveAll(oldRequests);
        log.info("Expired {} old pending access requests", oldRequests.size());
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void validateExpertRole(String expertProfileId) {
        Profile profile = profileRepository.findById(expertProfileId).orElseThrow(
                () -> new IllegalArgumentException("Expert profile not found: " + expertProfileId));
        if (profile.getRole() != ProfileRole.EXPERT) {
            throw new IllegalArgumentException("Only experts can request consulting data access");
        }
    }

    private void validateActiveConsultation(String farmerProfileId, String expertProfileId) {
        boolean active = userConnectionRepository
                .findByFollowerIdAndFollowingId(farmerProfileId, expertProfileId)
                .map(conn -> conn.getConsultationStatus() == ConsultationStatus.ACCEPTED)
                .orElse(false);
        if (!active) {
            throw new IllegalStateException("No active consultation exists with this farmer");
        }
    }

    private ConsultingDataAccessRequest findAndValidateOwner(String requestId, String farmerProfileId) {
        ConsultingDataAccessRequest request = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found: " + requestId));
        if (!request.getFarmerProfileId().equals(farmerProfileId)) {
            throw new IllegalStateException("You are not authorized to act on this request");
        }
        return request;
    }

    private void ensurePending(ConsultingDataAccessRequest request) {
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be approved or denied");
        }
    }

    private ConsultingDataAccessRequestResponse toResponse(ConsultingDataAccessRequest request) {
        Profile expertProfile = profileRepository.findById(request.getExpertProfileId()).orElse(null);
        return ConsultingDataAccessRequestResponse.builder()
                .id(request.getId())
                .expertProfileId(request.getExpertProfileId())
                .expertName(expertProfile != null ? expertProfile.getFullName() : null)
                .expertAvatar(expertProfile != null ? expertProfile.getProfilePicture() : null)
                .farmerProfileId(request.getFarmerProfileId())
                .dataType(request.getDataType())
                .status(request.getStatus())
                .expertMessage(request.getExpertMessage())
                .requestedAt(request.getCreatedAt())
                .respondedAt(request.getUpdatedAt())
                .build();
    }

    private void publishAccessRequestNotification(String expertProfileId, String farmerProfileId, ConsultingDataType dataType) {
        try {
            Profile expert = profileRepository.findById(expertProfileId).orElse(null);
            notificationPublisher.publish(RawNotificationEvent.builder()
                    .recipientId(farmerProfileId)
                    .actorId(expertProfileId)
                    .actorName(expert != null ? expert.getFullName() : expertProfileId)
                    .actorAvatar(expert != null ? expert.getProfilePicture() : null)
                    .type(NotificationType.CONSULTING_DATA_ACCESS_REQUEST)
                    .referenceId(farmerProfileId)
                    .payload(Map.of(
                            "dataType", dataType.name(),
                            "expertProfileId", expertProfileId
                    ))
                    .occurredAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[Notification] Failed to publish CONSULTING_DATA_ACCESS_REQUEST: expert={}, farmer={}",
                    expertProfileId, farmerProfileId, e);
        }
    }

    private void publishApprovalNotification(ConsultingDataAccessRequest request, boolean approved) {
        try {
            Profile farmer = profileRepository.findById(request.getFarmerProfileId()).orElse(null);
            NotificationType type = approved
                    ? NotificationType.CONSULTING_DATA_ACCESS_APPROVED
                    : NotificationType.CONSULTING_DATA_ACCESS_DENIED;
            notificationPublisher.publish(RawNotificationEvent.builder()
                    .recipientId(request.getExpertProfileId())
                    .actorId(request.getFarmerProfileId())
                    .actorName(farmer != null ? farmer.getFullName() : request.getFarmerProfileId())
                    .actorAvatar(farmer != null ? farmer.getProfilePicture() : null)
                    .type(type)
                    .referenceId(request.getFarmerProfileId())
                    .payload(Map.of(
                            "dataType", request.getDataType().name(),
                            "farmerProfileId", request.getFarmerProfileId()
                    ))
                    .occurredAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[Notification] Failed to publish access {} notification: requestId={}",
                    approved ? "approval" : "denial", request.getId(), e);
        }
    }
}
