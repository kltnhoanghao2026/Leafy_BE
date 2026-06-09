package com.leafy.authservice.controller;

import com.leafy.authservice.dto.response.DeviceResponse;
import com.leafy.authservice.model.Device;
import com.leafy.authservice.service.device.DeviceService;
import com.leafy.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Device management controller
 * Allows users to view and manage their registered devices
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {
    
    private final DeviceService deviceService;
    
    /**
     * Get all devices for the authenticated user
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getUserDevices(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Device-Id", required = false) String currentDeviceId) {
        
        List<Device> devices = deviceService.getUserDevices(userId);
        
        List<DeviceResponse> deviceResponses = devices.stream()
                .map(device -> mapToResponse(device, currentDeviceId))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(deviceResponses));
    }
    
    /**
     * Remove a specific device
     */
    @DeleteMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> removeDevice(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String deviceId) {
        
        deviceService.removeDevice(userId, deviceId);
        
        log.info("Device removed - User: {}, Device: {}", userId, deviceId);
        
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    /**
     * Remove all devices except the current one
     */
    @DeleteMapping("/others")
    public ResponseEntity<ApiResponse<Void>> removeOtherDevices(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Device-Id") String currentDeviceId) {
        
        List<Device> devices = deviceService.getUserDevices(userId);
        
        devices.stream()
                .filter(device -> !device.getDeviceId().equals(currentDeviceId))
                .forEach(device -> deviceService.removeDevice(userId, device.getDeviceId()));
        
        log.info("Removed other devices for user: {}", userId);
        
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    private DeviceResponse mapToResponse(Device device, String currentDeviceId) {
        return DeviceResponse.builder()
                .id(device.getId())
                .deviceId(device.getDeviceId())
                .deviceName(device.getDeviceName())
                .deviceType(device.getDeviceType())
                .platform(device.getPlatform())
                .browser(device.getBrowser())
                .osVersion(device.getOsVersion())
                .appVersion(device.getAppVersion())
                .lastUsedAt(device.getLastUsedAt())
                .firstSeenAt(device.getFirstSeenAt())
                .trusted(device.getTrusted())
                .isCurrentDevice(device.getDeviceId().equals(currentDeviceId))
                .build();
    }
}
