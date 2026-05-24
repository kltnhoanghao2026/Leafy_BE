package com.leafy.profileservice.service.certificate;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.profileservice.client.AuthClient;
import com.leafy.profileservice.client.dto.UserResponse;
import com.leafy.profileservice.dto.request.profile.CreateApprovalRequest;
import com.leafy.profileservice.dto.request.profile.UpdateCertificateStatusRequest;
import com.leafy.profileservice.dto.response.profile.ApprovalRequestDto;
import com.leafy.profileservice.dto.response.profile.ApprovalRequestResponse;
import com.leafy.profileservice.dto.response.profile.ApprovalRequestUserInfo;
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
import java.util.Map;
import java.util.stream.Collectors;

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

        List<Certificate> certificates = request.certificates().stream()
                .map(certificateMapper::toEntity)
                .toList();

        ApprovalRequest approvalRequest = ApprovalRequest.builder()
                .profileId(profileId)
                .certificates(certificates)
                .status(CertificateStatus.PENDING)
                .proposedSpecialty(request.proposedSpecialty())
                .build();

        approvalRequest = approvalRequestRepository.save(approvalRequest);
        log.info("Approval request {} submitted successfully.", approvalRequest.getId());

        return certificateMapper.toApprovalRequestDto(approvalRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getApprovalRequests(String profileId) {
        log.info("Fetching all approval requests for profile ID: {}", profileId);
        List<ApprovalRequest> requests = approvalRequestRepository.findByProfileId(profileId);
        return certificateMapper.toApprovalRequestDtoList(requests);
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
    public List<ApprovalRequestResponse> getPendingApprovalRequests() {
        log.info("Fetching all pending approval requests");

        List<ApprovalRequest> pending = approvalRequestRepository.findByStatus(CertificateStatus.PENDING);

        // Bulk-fetch profiles for all distinct profileIds
        List<String> profileIds = pending.stream()
                .map(ApprovalRequest::getProfileId)
                .distinct()
                .toList();

        Map<String, Profile> profileMap = profileRepository.findAllById(profileIds)
                .stream()
                .collect(Collectors.toMap(Profile::getId, p -> p));

        return pending.stream().map(req -> {
            ApprovalRequestResponse base = certificateMapper.toApprovalRequestResponse(req);
            Profile profile = profileMap.get(req.getProfileId());

            ApprovalRequestUserInfo userInfo = null;
            if (profile != null) {
                String email = resolveEmail(profile.getUserId());
                userInfo = new ApprovalRequestUserInfo(
                        profile.getFullName(),
                        profile.getAvatar(),
                        profile.getProfilePicture(),
                        profile.getRole(),
                        email,
                        Boolean.TRUE.equals(profile.getIsVerified())
                );
            }

            return new ApprovalRequestResponse(
                    base.id(),
                    base.profileId(),
                    base.certificates(),
                    base.status(),
                    base.rejectionReason(),
                    req.getProposedSpecialty(),
                    userInfo
            );
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalRequestResponse> getProcessedApprovalRequests() {
        log.info("Fetching all processed (non-pending) approval requests");

        List<ApprovalRequest> processed = approvalRequestRepository.findByStatusNot(CertificateStatus.PENDING);

        List<String> profileIds = processed.stream()
                .map(ApprovalRequest::getProfileId)
                .distinct()
                .toList();

        Map<String, Profile> profileMap = profileRepository.findAllById(profileIds)
                .stream()
                .collect(Collectors.toMap(Profile::getId, p -> p));

        return processed.stream().map(req -> {
            ApprovalRequestResponse base = certificateMapper.toApprovalRequestResponse(req);
            Profile profile = profileMap.get(req.getProfileId());

            ApprovalRequestUserInfo userInfo = null;
            if (profile != null) {
                String email = resolveEmail(profile.getUserId());
                userInfo = new ApprovalRequestUserInfo(
                        profile.getFullName(),
                        profile.getAvatar(),
                        profile.getProfilePicture(),
                        profile.getRole(),
                        email,
                        Boolean.TRUE.equals(profile.getIsVerified())
                );
            }

            return new ApprovalRequestResponse(
                    base.id(),
                    base.profileId(),
                    base.certificates(),
                    base.status(),
                    base.rejectionReason(),
                    req.getProposedSpecialty(),
                    userInfo
            );
        }).toList();
    }

    @Override
    public ProfileResponse revokeApprovalRequest(String profileId, String requestId, String reason) {
        log.info("Revoking approval request ID: {} for profile ID: {}", requestId, profileId);
        getProfileEntityById(profileId);

        ApprovalRequest approvalRequest = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED));

        if (!profileId.equals(approvalRequest.getProfileId())) {
            log.warn("Approval request {} does not belong to profile {}", requestId, profileId);
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }

        if (approvalRequest.getStatus() != CertificateStatus.APPROVED) {
            log.warn("Cannot revoke approval request {} with status {}", requestId, approvalRequest.getStatus());
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }

        approvalRequest.setStatus(CertificateStatus.REVOKED);
        approvalRequest.setRejectionReason(reason);
        approvalRequestRepository.save(approvalRequest);
        log.info("Approval request {} revoked successfully.", requestId);

        return buildFullProfileResponse(getProfileEntityById(profileId));
    }

    private String resolveEmail(String userId) {
        try {
            ApiResponse<UserResponse> apiResponse = authClient.getUserById(userId);
            if (apiResponse != null && apiResponse.data() != null) {
                return apiResponse.data().getEmail();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user info for userId: {}. Error: {}", userId, e.getMessage());
        }
        return null;
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
