package com.leafy.profileservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.dto.request.profile.RevokeApprovalRequest;
import com.leafy.profileservice.dto.request.profile.UpdateCertificateStatusRequest;
import com.leafy.profileservice.dto.response.profile.ApprovalRequestResponse;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import com.leafy.profileservice.service.certificate.CertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST Controller for Approval Request management across the platform.
 * Provides endpoints at /admin/certificates/approval-requests/* for listing and managing
 * all pending/processed approval requests.
 */
@RestController
@RequestMapping("/admin/certificates/approval-requests")
@RequiredArgsConstructor
@Slf4j
public class AdminApprovalController {

    private final CertificateService certificateService;

    /**
     * Get all pending approval requests across the platform
     *
     * @return list of pending approval requests
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ApprovalRequestResponse>>> getPendingApprovalRequests() {
        log.info("GET /admin/certificates/approval-requests/pending - Fetching all pending approval requests");
        List<ApprovalRequestResponse> response = certificateService.getPendingApprovalRequests();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get all processed (APPROVED, REJECTED, or REVOKED) approval requests across the platform
     *
     * @return list of processed approval requests
     */
    @GetMapping("/processed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ApprovalRequestResponse>>> getProcessedApprovalRequests() {
        log.info("GET /admin/certificates/approval-requests/processed - Fetching all processed approval requests");
        List<ApprovalRequestResponse> response = certificateService.getProcessedApprovalRequests();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Update the status of an approval request
     *
     * @param profileId the profile ID
     * @param requestId the approval request ID to update
     * @param request   the update payload
     * @return the updated profile response
     */
    @PatchMapping("/{profileId}/{requestId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> patchApprovalRequestStatus(
            @PathVariable String profileId,
            @PathVariable String requestId,
            @RequestBody UpdateCertificateStatusRequest request) {
        log.info("PATCH /admin/certificates/approval-requests/{}/{} - Updating request status to {}",
                profileId, requestId, request.getStatus());
        ProfileResponse response = certificateService.patchApprovalRequestStatus(profileId, requestId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Revoke a previously approved approval request
     *
     * @param profileId the profile ID
     * @param requestId the approval request ID to revoke
     * @param request   optional revocation reason
     * @return the updated profile response
     */
    @PatchMapping("/{profileId}/{requestId}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> revokeApprovalRequest(
            @PathVariable String profileId,
            @PathVariable String requestId,
            @RequestBody(required = false) RevokeApprovalRequest request) {
        log.info("PATCH /admin/certificates/approval-requests/{}/{} - Revoking approval request", profileId, requestId);
        String reason = request != null ? request.reason() : null;
        ProfileResponse response = certificateService.revokeApprovalRequest(profileId, requestId, reason);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
