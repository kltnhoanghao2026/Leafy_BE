package com.leafy.profileservice.service.certificate;

import com.leafy.profileservice.dto.ApprovalRequestDto;
import com.leafy.profileservice.dto.request.profile.CreateApprovalRequest;
import com.leafy.profileservice.dto.request.profile.UpdateCertificateStatusRequest;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;

import java.util.List;

/**
 * Service interface for Certificate management within Profiles
 */
public interface CertificateService {

    /**
     * Submit a new batch of certificates for approval
     *
     * @param profileId the profile ID
     * @param request   the batch certificate details
     * @return the newly created approval request
     */
    ApprovalRequestDto submitApprovalRequest(String profileId, CreateApprovalRequest request);

    /**
     * Update the status of an approval request
     *
     * @param profileId the profile ID
     * @param requestId the approval request ID to update
     * @param request   the update payload containing status and reason
     * @return the updated profile response
     */
    ProfileResponse patchApprovalRequestStatus(String profileId, String requestId,
            UpdateCertificateStatusRequest request);

    /**
     * Retrieve all pending approval requests across the platform
     *
     * @return list of pending approval request DTOs
     */
    List<ApprovalRequestDto> getPendingApprovalRequests();
}
