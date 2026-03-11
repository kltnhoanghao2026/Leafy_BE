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

    @NotBlank(message = "Title is required")
    String title;

    @NotBlank(message = "Issued by is required")
    String issuedBy;

    @NotBlank(message = "Proof URL is required")
    String proofUrl;

    @NotNull(message = "Issue date is required")
    LocalDate issueDate;
}
