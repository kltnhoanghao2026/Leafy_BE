package com.leafy.profileservice.dto.response.profile;

import java.time.LocalDate;

public record CertificateDto(
        String id,
        String title,
        String issuedBy,
        String proofUrl,
        String proofFileId,
        String fileType,
        LocalDate issueDate,
        boolean expired
) {
}