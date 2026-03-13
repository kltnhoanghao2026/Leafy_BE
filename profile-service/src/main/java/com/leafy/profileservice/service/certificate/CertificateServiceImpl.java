package com.leafy.profileservice.service.certificate;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.profileservice.client.AuthClient;
import com.leafy.profileservice.client.dto.UserResponse;
import com.leafy.profileservice.dto.ApprovalRequestDto;
import com.leafy.profileservice.dto.request.profile.CreateApprovalRequest;
import com.leafy.profileservice.dto.request.profile.UpdateCertificateStatusRequest;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import com.leafy.profileservice.model.enums.CertificateStatus;
import com.leafy.profileservice.mapper.CertificateMapper;
import com.leafy.profileservice.mapper.ProfileMapper;
import com.leafy.profileservice.model.ApprovalRequest;
import com.leafy.profileservice.model.Certificate;
import com.leafy.profileservice.model.Profile;
import com.leafy.profileservice.repository.ApprovalRequestRepository;
import com.leafy.profileservice.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CertificateServiceImpl implements CertificateService {

    private final ProfileRepository profileRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ProfileMapper profileMapper;
    private final CertificateMapper certificateMapper;
    private final AuthClient authClient;

    @Override
    public ApprovalRequestDto submitApprovalRequest(String profileId, CreateApprovalRequest request) {
        log.info("Submitting new approval request for profile ID: {}", profileId);
        getProfileEntityById(profileId); // Ensure profile exists

        List<Certificate> certificates = request.getCertificates().stream()
                .map(certificateMapper::toEntity)
                .toList();

        ApprovalRequest approvalRequest = ApprovalRequest.builder()
                .profileId(profileId)
                .certificates(certificates)
                .status(CertificateStatus.PENDING)
                .build();

        approvalRequest = approvalRequestRepository.save(approvalRequest);
        log.info("Approval request {} submitted successfully.", approvalRequest.getId());

        return certificateMapper.toApprovalRequestDto(approvalRequest);
    }

    @Override
    public ProfileResponse patchApprovalRequestStatus(String profileId, String requestId,
            UpdateCertificateStatusRequest request) {
        log.info("Updating status for approval request ID: {} in profile ID: {} to {}", requestId, profileId,
                request.getStatus());
        getProfileEntityById(profileId); // Ensure profile exists

        ApprovalRequest approvalRequest = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED)); // Adjust error code properly ideally

        if (!profileId.equals(approvalRequest.getProfileId())) {
            log.warn("Approval request {} does not belong to profile {}", requestId, profileId);
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }

        approvalRequest.setStatus(request.getStatus());
        if (request.getStatus() == CertificateStatus.REJECTED) {
            approvalRequest.setRejectionReason(request.getReason());
        } else {
            approvalRequest.setRejectionReason(null); // Clear reason if modifying from Rejected -> Approved
        }

        approvalRequestRepository.save(approvalRequest);
        log.info("Approval request {} status updated successfully.", requestId);

        return buildFullProfileResponse(getProfileEntityById(profileId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getPendingApprovalRequests() {
        log.info("Fetching all pending approval requests");
        return certificateMapper
                .toApprovalRequestDtoList(approvalRequestRepository.findByStatus(CertificateStatus.PENDING));
    }

    private ProfileResponse buildFullProfileResponse(Profile profile) {
        ProfileResponse response = profileMapper.toResponse(profile);

        // Map ONLY APPROVED certificates back to the profile wrapper
        List<Certificate> allApprovedCertificates = approvalRequestRepository.findByProfileId(profile.getId())
                .stream()
                .filter(req -> req.getStatus() == CertificateStatus.APPROVED)
                .flatMap(req -> req.getCertificates().stream())
                .toList();

        response.setCertificates(certificateMapper.toDtoList(allApprovedCertificates));

        return enrichWithUserInfo(response, profile.getUserId());
    }

    private Profile getProfileEntityById(String profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> {
                    log.error("Profile not found with ID: {}", profileId);
                    return new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND);
                });
    }

    private ProfileResponse enrichWithUserInfo(ProfileResponse response, String userId) {
        try {
            ApiResponse<UserResponse> apiResponse = authClient.getUserById(userId);
            if (apiResponse != null && apiResponse.data() != null) {
                UserResponse user = apiResponse.data();
                response.setEmail(user.getEmail());
                response.setPhoneNumber(user.getPhoneNumber());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user info for userId: {}. Error: {}", userId, e.getMessage());
        }
        return response;
    }
}
