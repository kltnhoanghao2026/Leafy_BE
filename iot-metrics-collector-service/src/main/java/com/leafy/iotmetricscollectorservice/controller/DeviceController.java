package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.device.ClaimDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceConfigResponse;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceResponse;
import com.leafy.iotmetricscollectorservice.dto.device.GenerateClaimCodeResponse;
import com.leafy.iotmetricscollectorservice.dto.device.ProvisionDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.device.UpdateDeviceConfigRequest;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigService;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigPushService;
import com.leafy.iotmetricscollectorservice.service.DeviceService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/iot/devices")
@RequiredArgsConstructor
public class DeviceController {

    static final String USER_ID_HEADER = "X-User-Id";

    private final DeviceService deviceService;
    private final DeviceConfigService deviceConfigService;
    private final DeviceConfigPushService deviceConfigPushService;

    @PostMapping("/provision")
    public ResponseEntity<DeviceResponse> provisionDevice(@RequestBody ProvisionDeviceRequest request) {
        return ResponseEntity.ok(deviceService.provisionDevice(request));
    }

    @PostMapping("/{deviceId}/claim-code")
    public ResponseEntity<GenerateClaimCodeResponse> generateClaimCode(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(deviceService.generateClaimCode(deviceId));
    }

    @PostMapping("/claim")
    public ResponseEntity<DeviceResponse> claimDevice(
        @RequestHeader(USER_ID_HEADER) UUID currentUserId,
        @RequestBody ClaimDeviceRequest request
    ) {
        return ResponseEntity.ok(deviceService.claimDevice(currentUserId, request));
    }

    @GetMapping("/me")
    public ResponseEntity<List<DeviceResponse>> getMyDevices(@RequestHeader(USER_ID_HEADER) UUID currentUserId) {
        return ResponseEntity.ok(deviceService.getDevicesByOwner(currentUserId));
    }

    @GetMapping("/{deviceId}/config")
    public ResponseEntity<DeviceConfigResponse> getDeviceConfig(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(deviceConfigService.getDeviceConfig(deviceId));
    }

    @PutMapping("/{deviceId}/config")
    public ResponseEntity<DeviceConfigResponse> updateDeviceConfig(
        @PathVariable UUID deviceId,
        @RequestBody UpdateDeviceConfigRequest request
    ) {
        return ResponseEntity.ok(deviceConfigService.updateDeviceConfig(deviceId, request));
    }

    @PostMapping("/{deviceId}/config/push")
    public ResponseEntity<DeviceConfigResponse> pushDeviceConfig(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(deviceConfigPushService.pushConfig(deviceId));
    }
}
