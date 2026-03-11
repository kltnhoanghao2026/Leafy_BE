package com.leafy.profileservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.dto.ApprovalRequestDto;
import com.leafy.profileservice.dto.request.profile.CreateApprovalRequest;
import com.leafy.profileservice.dto.request.profile.UpdateCertificateStatusRequest;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import com.leafy.profileservice.service.certificate.CertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Approval Request management within Profiles
 */
@RestController
@RequestMapping("/profiles/{profileId}/approval-requests")
@RequiredArgsConstructor
@Slf4j
public class CertificateController {

    private final CertificateService certificateService;

    /**
     * Submit a new batch of certificates for approval
     *
     * @param profileId the profile ID
     * @param request   the batch of certificates details
     * @return the created approval request response
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @profileSecurityService.isOwner(#profileId)")
    public ResponseEntity<ApiResponse<ApprovalRequestDto>> submitApprovalRequest(
            @PathVariable String profileId,
            @Valid @RequestBody CreateApprovalRequest request) {
        log.info("POST /profiles/{}/approval-requests - Submitting approval request", profileId);
        ApprovalRequestDto response = certificateService.submitApprovalRequest(profileId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * Update the status of an approval request
     *
     * @param profileId the profile ID
     * @param requestId the approval request ID to update
     * @param request   the update payload
     * @return the updated profile response
     */
    @PatchMapping("/{requestId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> patchApprovalRequestStatus(
            @PathVariable String profileId,
            @PathVariable String requestId,
            @Valid @RequestBody UpdateCertificateStatusRequest request) {
        log.info("PATCH /profiles/{}/approval-requests/{}/status - Updating request status to {}", profileId, requestId,
                request.getStatus());
        ProfileResponse response = certificateService.patchApprovalRequestStatus(profileId, requestId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get all pending approval requests across the platform
     *
     * @return list of pending approval requests
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ApprovalRequestDto>>> getPendingApprovalRequests() {
        log.info("GET /profiles/approval-requests/pending - Fetching all pending approval requests");
        List<ApprovalRequestDto> response = certificateService.getPendingApprovalRequests();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
