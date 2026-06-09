package com.leafy.profileservice.dto.request.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateApprovalRequest(
        @NotEmpty(message = "{validation.certificates.notEmpty}")
        @Valid
        List<AddCertificateRequest> certificates,
        String proposedSpecialty
) {
}
