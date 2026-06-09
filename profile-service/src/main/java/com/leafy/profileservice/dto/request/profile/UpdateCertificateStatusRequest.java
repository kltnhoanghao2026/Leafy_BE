package com.leafy.profileservice.dto.request.profile;

import com.leafy.profileservice.model.enums.CertificateStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCertificateStatusRequest {

    @NotNull(message = "{validation.certificate.status.required}")
    CertificateStatus status;

    String reason;
}
