package com.leafy.profileservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.profileservice.dto.response.access.ConsultingDataAccessRequestResponse;
import com.leafy.profileservice.model.enums.ConsultingDataType;
import com.leafy.profileservice.service.access.ConsultingDataAccessRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for managing consulting data access requests.
 * Allows experts to request data access and farmers to approve/deny.
 */
@RestController
@RequestMapping("/profiles/consulting/access")
@RequiredArgsConstructor
@Slf4j
public class ConsultingDataAccessRequestController {

    private final ConsultingDataAccessRequestService accessRequestService;

    /**
     * Expert requests access to a specific data type of a consulted farmer.
     */
    @PostMapping("/request")
    @PreAuthorize("hasRole('EXPERT')")
    public ResponseEntity<ApiResponse<ConsultingDataAccessRequestResponse>> requestAccess(
            @RequestParam ConsultingDataType dataType,
            @RequestParam String farmerProfileId,
            @RequestParam(required = false) String message) {
        String expertProfileId = ServiceSecurityUtils.getCurrentProfileId();
        log.info("POST /profiles/consulting/access/request - expert={}, farmer={}, dataType={}",
                expertProfileId, farmerProfileId, dataType);
        ConsultingDataAccessRequestResponse response = accessRequestService
                .requestAccess(expertProfileId, farmerProfileId, dataType, message);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get all pending access requests for the current farmer (for approval UI).
     */
    @GetMapping("/requests/pending")
    public ResponseEntity<ApiResponse<Page<ConsultingDataAccessRequestResponse>>> getPendingRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String farmerProfileId = ServiceSecurityUtils.getCurrentProfileId();
        log.info("GET /profiles/consulting/access/requests/pending - farmer={}", farmerProfileId);
        Page<ConsultingDataAccessRequestResponse> requests = accessRequestService
                .getPendingRequestsForFarmer(farmerProfileId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    /**
     * Farmer approves a pending access request.
     */
    @PostMapping("/requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<ConsultingDataAccessRequestResponse>> approveRequest(
            @PathVariable String requestId) {
        String farmerProfileId = ServiceSecurityUtils.getCurrentProfileId();
        log.info("POST /profiles/consulting/access/requests/{}/approve - farmer={}", requestId, farmerProfileId);
        ConsultingDataAccessRequestResponse response = accessRequestService.approveRequest(requestId, farmerProfileId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Farmer denies a pending access request.
     */
    @PostMapping("/requests/{requestId}/deny")
    public ResponseEntity<ApiResponse<ConsultingDataAccessRequestResponse>> denyRequest(
            @PathVariable String requestId) {
        String farmerProfileId = ServiceSecurityUtils.getCurrentProfileId();
        log.info("POST /profiles/consulting/access/requests/{}/deny - farmer={}", requestId, farmerProfileId);
        ConsultingDataAccessRequestResponse response = accessRequestService.denyRequest(requestId, farmerProfileId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
