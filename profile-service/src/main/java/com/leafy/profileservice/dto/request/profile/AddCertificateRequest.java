package com.leafy.profileservice.dto.request.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AddCertificateRequest(
        @NotBlank(message = "{validation.certificate.title.required}")
        String title,

        @NotBlank(message = "{validation.certificate.issuedBy.required}")
        String issuedBy,

        @NotBlank(message = "{validation.certificate.proofUrl.required}")
        String proofUrl,

        String proofFileId,

        String fileType,

        @NotNull(message = "{validation.certificate.issueDate.required}")
        LocalDate issueDate
) {
}
