package com.leafy.profileservice.mapper;

import com.leafy.profileservice.dto.ApprovalRequestDto;
import com.leafy.profileservice.dto.CertificateDto;
import com.leafy.profileservice.dto.request.profile.AddCertificateRequest;
import com.leafy.profileservice.model.ApprovalRequest;
import com.leafy.profileservice.model.Certificate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * Mapper interface for Certificate entity and DTOs
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CertificateMapper {

    /**
     * Map AddCertificateRequest to Certificate entity
     *
     * @param request the create request
     * @return the certificate entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "expired", ignore = true)
    Certificate toEntity(AddCertificateRequest request);

    /**
     * Map Certificate entity to DTO
     *
     * @param certificate the certificate entity
     * @return the certificate DTO
     */
    CertificateDto toDto(Certificate certificate);

    /**
     * Map list of Certificates to DTOs
     */
    List<CertificateDto> toDtoList(List<Certificate> certificates);

    /**
     * Map ApprovalRequest to ApprovalRequestDto
     *
     * @param approvalRequest the approval request entity
     * @return the approval request DTO
     */
    ApprovalRequestDto toApprovalRequestDto(ApprovalRequest approvalRequest);

    /**
     * Map list of ApprovalRequests to DTOs
     */
    List<ApprovalRequestDto> toApprovalRequestDtoList(List<ApprovalRequest> requests);
}
