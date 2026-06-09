package com.leafy.profileservice.service.certificate;

import com.leafy.profileservice.dto.request.profile.CreateApprovalRequest;
import com.leafy.profileservice.dto.request.profile.UpdateCertificateStatusRequest;
import com.leafy.profileservice.dto.response.profile.ApprovalRequestDto;
import com.leafy.profileservice.dto.response.profile.ApprovalRequestResponse;
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
     * Get all approval requests for a specific profile (owner view)
     *
     * @param profileId the profile ID
     * @return list of all approval requests for the profile
     */
    List<ApprovalRequestDto> getApprovalRequests(String profileId);

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
     * Retrieve all pending approval requests across the platform, enriched with profile/user info.
     *
     * @return list of pending approval request responses
     */
    List<ApprovalRequestResponse> getPendingApprovalRequests();

    /**
     * Retrieve all processed (APPROVED or REJECTED) approval requests, enriched with profile/user info.
     *
     * @return list of processed approval request responses
     */
    List<ApprovalRequestResponse> getProcessedApprovalRequests();

    /**
     * Revoke a previously approved approval request, removing the certificates from the profile.
     *
     * @param profileId the profile ID
     * @param requestId the approval request ID to revoke
     * @param reason    optional reason for revocation
     * @return the updated profile response
     */
    ProfileResponse revokeApprovalRequest(String profileId, String requestId, String reason);
}
