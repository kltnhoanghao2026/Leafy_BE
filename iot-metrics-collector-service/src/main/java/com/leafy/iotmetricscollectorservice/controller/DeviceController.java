package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.device.ClaimDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.device.ConnectDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceConfigResponse;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceResponse;
import com.leafy.iotmetricscollectorservice.dto.device.GenerateClaimCodeResponse;
import com.leafy.iotmetricscollectorservice.dto.device.ProvisionDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.device.UpdateDeviceConfigRequest;
import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureRequest;
import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureResponse;
import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaEventResponse;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceStatus;
import com.leafy.iotmetricscollectorservice.model.enums.ProvisioningStatus;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigService;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigPushService;
import com.leafy.iotmetricscollectorservice.service.DeviceService;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaService;
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
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/iot/devices")
@RequiredArgsConstructor
public class DeviceController {

    static final String USER_ID_HEADER = "X-User-Id";

    private final DeviceService deviceService;
    private final DeviceConfigService deviceConfigService;
    private final DeviceConfigPushService deviceConfigPushService;
    private final DeviceMediaService deviceMediaService;

    @PostMapping("/provision")
    public ResponseEntity<DeviceResponse> provisionDevice(@RequestBody ProvisionDeviceRequest request) {
        return ResponseEntity.ok(deviceService.provisionDevice(request));
    }

    @PostMapping("/connect")
    public ResponseEntity<DeviceResponse> connectDevice(
        @RequestHeader(USER_ID_HEADER) String currentUserId,
        @RequestBody ConnectDeviceRequest request
    ) {
        return ResponseEntity.ok(deviceService.connectDevice(currentUserId, request));
    }

    @PostMapping("/{deviceId}/claim-code")
    public ResponseEntity<GenerateClaimCodeResponse> generateClaimCode(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(deviceService.generateClaimCode(deviceId));
    }

    @PostMapping("/claim")
    public ResponseEntity<DeviceResponse> claimDevice(
        @RequestHeader(USER_ID_HEADER) String currentUserId,
        @RequestBody ClaimDeviceRequest request
    ) {
        return ResponseEntity.ok(deviceService.claimDevice(currentUserId, request));
    }

    @GetMapping("/me")
    public ResponseEntity<PagedResponse<DeviceResponse>> getMyDevices(
        @RequestHeader(USER_ID_HEADER) String currentUserId,
        @RequestParam(defaultValue = "0") Integer page,
        @RequestParam(defaultValue = "20") Integer size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir,
        @RequestParam(required = false) DeviceStatus status,
        @RequestParam(required = false) ProvisioningStatus provisioningStatus,
        @RequestParam(required = false) String zoneId,
        @RequestParam(required = false) String farmPlotId,
        @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(
            deviceService.getDevicesByOwner(
                currentUserId,
                page,
                size,
                sortBy,
                sortDir,
                status,
                provisioningStatus,
                zoneId,
                farmPlotId,
                keyword
            )
        );
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

    @PostMapping("/{deviceId}/camera/capture")
    public ResponseEntity<CameraCaptureResponse> captureDeviceImage(
        @PathVariable UUID deviceId,
        @RequestBody(required = false) CameraCaptureRequest request
    ) {
        return ResponseEntity.ok(deviceMediaService.requestCapture(deviceId, request));
    }

    @GetMapping("/{deviceId}/media")
    public ResponseEntity<List<DeviceMediaEventResponse>> getDeviceMedia(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(deviceMediaService.listDeviceMedia(deviceId));
    }
}
