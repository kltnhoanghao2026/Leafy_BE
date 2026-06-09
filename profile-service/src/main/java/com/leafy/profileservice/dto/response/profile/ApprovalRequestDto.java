package com.leafy.profileservice.dto.response.profile;

import com.leafy.profileservice.model.enums.CertificateStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ApprovalRequestDto(
        String id,
        String profileId,
        List<CertificateDto> certificates,
        CertificateStatus status,
        String rejectionReason,
        String proposedSpecialty,
        LocalDateTime createdAt
) {
}