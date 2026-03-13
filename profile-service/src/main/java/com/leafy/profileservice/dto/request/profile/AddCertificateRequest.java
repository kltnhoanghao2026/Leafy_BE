package com.leafy.profileservice.dto.request.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AddCertificateRequest {

    @NotBlank(message = "{validation.certificate.title.required}")
    String title;

    @NotBlank(message = "{validation.certificate.issuedBy.required}")
    String issuedBy;

    @NotBlank(message = "{validation.certificate.proofUrl.required}")
    String proofUrl;

    @NotNull(message = "{validation.certificate.issueDate.required}")
    LocalDate issueDate;
}
