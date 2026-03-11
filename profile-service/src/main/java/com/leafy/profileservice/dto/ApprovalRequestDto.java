package com.leafy.profileservice.dto;

import com.leafy.profileservice.model.enums.CertificateStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApprovalRequestDto {
    String id;
    String profileId;
    List<CertificateDto> certificates;
    CertificateStatus status;
    String rejectionReason;
}
