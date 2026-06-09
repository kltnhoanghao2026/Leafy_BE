package com.leafy.profileservice.dto.response.profile;

import com.leafy.profileservice.model.enums.CertificateStatus;

import java.util.List;

/**
 * Approval request response enriched with profile/user info.
 * Returned by admin endpoints (e.g. GET /profiles/admin/approval-requests/pending).
 */
public record ApprovalRequestResponse(
        String id,
        String profileId,
        List<CertificateDto> certificates,
        CertificateStatus status,
        String rejectionReason,
        String proposedSpecialty,
        ApprovalRequestUserInfo userInfo
) {
}
