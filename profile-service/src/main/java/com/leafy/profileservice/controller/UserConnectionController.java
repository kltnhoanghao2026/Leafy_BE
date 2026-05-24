package com.leafy.profileservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.dto.response.profile.UserConnectionResponse;
import com.leafy.profileservice.service.connection.UserConnectionService;
import com.leafy.profileservice.service.profile.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.leafy.profileservice.dto.response.profile.ConsultationRequestResponse;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ProfileService profileService;

    // ── Follow / Unfollow ────────────────────────────────────────────────────

    @PostMapping("/users/{followingProfileId}/follow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserConnectionResponse>> followUser(
            @RequestHeader("X-User-Id") String followerUserId,
            @PathVariable String followingProfileId) {
        log.info("POST /profiles/users/{}/follow by userId: {}", followingProfileId, followerUserId);
        String followerProfileId = profileService.getProfileIdByUserId(followerUserId);
        UserConnectionResponse connection = userConnectionService.followUser(followerProfileId, followingProfileId);
        return ResponseEntity.ok(ApiResponse.success(connection));
    }

    @PostMapping("/users/{followingProfileId}/unfollow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> unfollowUser(
            @RequestHeader("X-User-Id") String followerUserId,
            @PathVariable String followingProfileId) {
        log.info("POST /profiles/users/{}/unfollow by userId: {}", followingProfileId, followerUserId);
        String followerProfileId = profileService.getProfileIdByUserId(followerUserId);
        userConnectionService.unfollowUser(followerProfileId, followingProfileId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Consultation ─────────────────────────────────────────────────────────

    @PostMapping("/experts/{expertProfileId}/consult/request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserConnectionResponse>> requestConsultation(
            @RequestHeader("X-User-Id") String farmerUserId,
            @PathVariable String expertProfileId) {
        log.info("POST /profiles/experts/{}/consult/request by userId: {}", expertProfileId, farmerUserId);
        String farmerProfileId = profileService.getProfileIdByUserId(farmerUserId);
        UserConnectionResponse connection = userConnectionService.requestConsultation(farmerProfileId, expertProfileId);
        return ResponseEntity.ok(ApiResponse.success(connection));
    }

    @PostMapping("/experts/{expertProfileId}/consult/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancelConsultation(
            @RequestHeader("X-User-Id") String farmerUserId,
            @PathVariable String expertProfileId) {
        log.info("POST /profiles/experts/{}/consult/cancel by userId: {}", expertProfileId, farmerUserId);
        String farmerProfileId = profileService.getProfileIdByUserId(farmerUserId);
        userConnectionService.cancelConsultationRequest(farmerProfileId, expertProfileId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/experts/consult/respond")
    @PreAuthorize("@profileSecurityService.isExpert()")
    public ResponseEntity<ApiResponse<UserConnectionResponse>> respondToConsultation(
            @RequestHeader("X-User-Id") String expertUserId,
            @RequestParam String farmerProfileId,
            @RequestParam boolean accept) {
        log.info("POST /profiles/experts/consult/respond userId: {}, farmer: {}, accept: {}", expertUserId, farmerProfileId, accept);
        String expertProfileId = profileService.getProfileIdByUserId(expertUserId);
        UserConnectionResponse connection = userConnectionService.respondToConsultationRequest(expertProfileId, farmerProfileId, accept);
        return ResponseEntity.ok(ApiResponse.success(connection));
    }

    // ── Query endpoints ───────────────────────────────────────────────────────

    @GetMapping("/experts/consult/pending")
    @PreAuthorize("@profileSecurityService.isExpert()")
    public ResponseEntity<ApiResponse<Page<ConsultationRequestResponse>>> getPendingConsultations(
            @RequestHeader("X-User-Id") String expertUserId,
            Pageable pageable) {
        log.info("GET /profiles/experts/consult/pending by userId: {}", expertUserId);
        String expertProfileId = profileService.getProfileIdByUserId(expertUserId);
        var page = userConnectionService.getPendingConsultations(expertProfileId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/experts/consult/accepted")
    @PreAuthorize("@profileSecurityService.isExpert()")
    public ResponseEntity<ApiResponse<Page<ConsultationRequestResponse>>> getAcceptedConsultations(
            @RequestHeader("X-User-Id") String expertUserId,
            Pageable pageable) {
        log.info("GET /profiles/experts/consult/accepted by userId: {}", expertUserId);
        String expertProfileId = profileService.getProfileIdByUserId(expertUserId);
        var page = userConnectionService.getAcceptedConsultations(expertProfileId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/me/following")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<String>>> getMyFollowingUsers(
            @RequestHeader("X-User-Id") String userId) {
        log.info("GET /profiles/me/following by userId: {}", userId);
        String profileId = profileService.getProfileIdByUserId(userId);
        List<String> followingUsers = userConnectionService.getFollowingUsers(profileId);
        return ResponseEntity.ok(ApiResponse.success(followingUsers));
    }

    @GetMapping("/users/{profileId}/followers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<String>>> getUserFollowers(
            @PathVariable String profileId) {
        log.info("GET /profiles/users/{}/followers", profileId);
        List<String> followers = userConnectionService.getUserFollowers(profileId);
        return ResponseEntity.ok(ApiResponse.success(followers));
    }

    @GetMapping("/users/{profileId}/followers/profiles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ProfileResponse>>> getUserFollowerProfiles(
            @PathVariable String profileId,
            Pageable pageable) {
        log.info("GET /profiles/users/{}/followers/profiles", profileId);
        var page = userConnectionService.getUserFollowerProfiles(profileId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/users/{profileId}/following/profiles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ProfileResponse>>> getUserFollowingProfiles(
            @PathVariable String profileId,
            Pageable pageable) {
        log.info("GET /profiles/users/{}/following/profiles", profileId);
        var page = userConnectionService.getUserFollowingProfiles(profileId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }
}
