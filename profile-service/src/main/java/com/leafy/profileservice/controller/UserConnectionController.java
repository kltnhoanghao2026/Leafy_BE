package com.leafy.profileservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.model.UserConnection;
import com.leafy.profileservice.service.connection.UserConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
@Slf4j
public class UserConnectionController {

    private final UserConnectionService userConnectionService;

    @PostMapping("/users/{followingId}/follow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserConnection>> followUser(
            @RequestHeader("X-User-Id") String followerId,
            @PathVariable String followingId) {
        log.info("POST /profiles/users/{}/follow by follower: {}", followingId, followerId);
        UserConnection connection = userConnectionService.followUser(followerId, followingId);
        return ResponseEntity.ok(ApiResponse.success(connection));
    }

    @PostMapping("/users/{followingId}/unfollow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> unfollowUser(
            @RequestHeader("X-User-Id") String followerId,
            @PathVariable String followingId) {
        log.info("POST /profiles/users/{}/unfollow by follower: {}", followingId, followerId);
        userConnectionService.unfollowUser(followerId, followingId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/experts/{expertId}/consult/request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserConnection>> requestConsultation(
            @RequestHeader("X-User-Id") String farmerId,
            @PathVariable String expertId) {
        log.info("POST /profiles/experts/{}/consult/request by farmer: {}", expertId, farmerId);
        UserConnection connection = userConnectionService.requestConsultation(farmerId, expertId);
        return ResponseEntity.ok(ApiResponse.success(connection));
    }

    @PostMapping("/experts/{expertId}/consult/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancelConsultation(
            @RequestHeader("X-User-Id") String farmerId,
            @PathVariable String expertId) {
        log.info("POST /profiles/experts/{}/consult/cancel by farmer: {}", expertId, farmerId);
        userConnectionService.cancelConsultationRequest(farmerId, expertId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/experts/consult/respond")
    @PreAuthorize("@profileSecurityService.isExpert()")
    public ResponseEntity<ApiResponse<UserConnection>> respondToConsultation(
            @RequestHeader("X-User-Id") String expertId,
            @RequestParam String farmerId,
            @RequestParam boolean accept) {
        log.info("POST /profiles/experts/consult/respond expert: {}, farmer: {}, accept: {}", expertId, farmerId, accept);
        UserConnection connection = userConnectionService.respondToConsultationRequest(expertId, farmerId, accept);
        return ResponseEntity.ok(ApiResponse.success(connection));
    }

    @GetMapping("/experts/consult/pending")
    @PreAuthorize("@profileSecurityService.isExpert()")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.leafy.profileservice.dto.response.profile.ConsultationRequestResponse>>> getPendingConsultations(
            @RequestHeader("X-User-Id") String expertId,
            org.springframework.data.domain.Pageable pageable) {
        log.info("GET /profiles/experts/consult/pending by expert: {}", expertId);
        var page = userConnectionService.getPendingConsultations(expertId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/experts/consult/accepted")
    @PreAuthorize("@profileSecurityService.isExpert()")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.leafy.profileservice.dto.response.profile.ConsultationRequestResponse>>> getAcceptedConsultations(
            @RequestHeader("X-User-Id") String expertId,
            org.springframework.data.domain.Pageable pageable) {
        log.info("GET /profiles/experts/consult/accepted by expert: {}", expertId);
        var page = userConnectionService.getAcceptedConsultations(expertId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/me/following")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<String>>> getMyFollowingUsers(
            @RequestHeader("X-User-Id") String userId) {
        log.info("GET /profiles/me/following by user: {}", userId);
        List<String> followingUsers = userConnectionService.getFollowingUsers(userId);
        return ResponseEntity.ok(ApiResponse.success(followingUsers));
    }
    
    @GetMapping("/users/{userId}/followers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<String>>> getUserFollowers(
            @PathVariable String userId) {
        log.info("GET /profiles/users/{}/followers", userId);
        List<String> followers = userConnectionService.getUserFollowers(userId);
        return ResponseEntity.ok(ApiResponse.success(followers));
    }
}
