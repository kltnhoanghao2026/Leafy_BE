package com.leafy.profileservice.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import java.time.LocalDate;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateDto {
    String id;
    String title;
    String issuedBy;
    String proofUrl;
    LocalDate issueDate;
    boolean expired;
}
