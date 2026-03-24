package com.leafy.profileservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.dto.request.preferences.*;
import com.leafy.profileservice.dto.response.preferences.UserPreferenceResponse;
import com.leafy.profileservice.model.UserPreference;
import com.leafy.profileservice.service.preferences.UserPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for User Preference management
 * Provides endpoints for managing user preferences with optimized MongoDB
 * queries
 */
@RestController
@RequestMapping("/preferences")
@RequiredArgsConstructor
@Slf4j
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    /**
     * Get current user's preferences
     *
     * @return user preference response
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> getMyPreferences() {
        log.info("GET /preferences/me - Getting current user's preferences");
        UserPreferenceResponse response = userPreferenceService.getMyPreferences();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get preferences by user ID
     *
     * @param userId the user ID
     * @return user preference response
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> getPreferencesByUserId(@PathVariable String userId) {
        log.info("GET /preferences/user/{} - Getting user preferences", userId);
        UserPreferenceResponse response = userPreferenceService.getPreferencesByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Update general settings
     *
     * @param request the update request
     * @return updated preferences
     */
    @PatchMapping("/general")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> updateGeneralSettings(
            @Valid @RequestBody GeneralSettingsUpdateRequest request) {
        log.info("PATCH /preferences/general - Updating general settings");
        UserPreferenceResponse response = userPreferenceService.updateGeneralSettings(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Update security settings
     *
     * @param request the update request
     * @return updated preferences
     */
    @PatchMapping("/security")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> updateSecuritySettings(
            @Valid @RequestBody SecuritySettingsUpdateRequest request) {
        log.info("PATCH /preferences/security - Updating security settings");
        UserPreferenceResponse response = userPreferenceService.updateSecuritySettings(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Update privacy settings
     *
     * @param request the update request
     * @return updated preferences
     */
    @PatchMapping("/privacy")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> updatePrivacySettings(
            @Valid @RequestBody PrivacySettingsUpdateRequest request) {
        log.info("PATCH /preferences/privacy - Updating privacy settings");
        UserPreferenceResponse response = userPreferenceService.updatePrivacySettings(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Update sync settings
     *
     * @param request the update request
     * @return updated preferences
     */
    @PatchMapping("/sync")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> updateSyncSettings(
            @Valid @RequestBody SyncSettingsUpdateRequest request) {
        log.info("PATCH /preferences/sync - Updating sync settings");
        UserPreferenceResponse response = userPreferenceService.updateSyncSettings(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Update appearance settings
     *
     * @param request the update request
     * @return updated preferences
     */
    @PatchMapping("/appearance")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> updateAppearanceSettings(
            @Valid @RequestBody AppearanceSettingsUpdateRequest request) {
        log.info("PATCH /preferences/appearance - Updating appearance settings");
        UserPreferenceResponse response = userPreferenceService.updateAppearanceSettings(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Update message settings
     *
     * @param request the update request
     * @return updated preferences
     */
    @PatchMapping("/message")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> updateMessageSettings(
            @Valid @RequestBody MessageSettingsUpdateRequest request) {
        log.info("PATCH /preferences/message - Updating message settings");
        UserPreferenceResponse response = userPreferenceService.updateMessageSettings(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Update notification settings
     *
     * @param request the update request
     * @return updated preferences
     */
    @PatchMapping("/notification")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> updateNotificationSettings(
            @Valid @RequestBody NotificationSettingsUpdateRequest request) {
        log.info("PATCH /preferences/notification - Updating notification settings");
        UserPreferenceResponse response = userPreferenceService.updateNotificationSettings(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Update utilities settings
     *
     * @param request the update request
     * @return updated preferences
     */
    @PatchMapping("/utilities")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> updateUtilitiesSettings(
            @Valid @RequestBody UtilitiesSettingsUpdateRequest request) {
        log.info("PATCH /preferences/utilities - Updating utilities settings");
        UserPreferenceResponse response = userPreferenceService.updateUtilitiesSettings(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get general settings only
     *
     * @return general settings
     */
    @GetMapping("/general")
    public ResponseEntity<ApiResponse<UserPreference.GeneralSettings>> getGeneralSettings() {
        log.info("GET /preferences/general - Getting general settings");
        UserPreference.GeneralSettings response = userPreferenceService.getGeneralSettings();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get privacy settings only
     *
     * @return privacy settings
     */
    @GetMapping("/privacy")
    public ResponseEntity<ApiResponse<UserPreference.PrivacySettings>> getPrivacySettings() {
        log.info("GET /preferences/privacy - Getting privacy settings");
        UserPreference.PrivacySettings response = userPreferenceService.getPrivacySettings();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get notification settings only
     *
     * @return notification settings
     */
    @GetMapping("/notification")
    public ResponseEntity<ApiResponse<UserPreference.NotificationSettings>> getNotificationSettings() {
        log.info("GET /preferences/notification - Getting notification settings");
        UserPreference.NotificationSettings response = userPreferenceService.getNotificationSettings();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Reset all preferences to defaults
     *
     * @return reset preferences
     */
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> resetToDefaults() {
        log.info("POST /preferences/reset - Resetting preferences to defaults");
        UserPreferenceResponse response = userPreferenceService.resetToDefaults();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
