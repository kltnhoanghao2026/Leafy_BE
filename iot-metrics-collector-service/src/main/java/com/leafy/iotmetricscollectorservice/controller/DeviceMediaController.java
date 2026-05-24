package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaEventResponse;
import com.leafy.iotmetricscollectorservice.service.DeviceAccessService;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/iot/media-events")
@RequiredArgsConstructor
public class DeviceMediaController {

    static final String USER_ID_HEADER = DeviceController.USER_ID_HEADER;

    private final DeviceMediaService deviceMediaService;
    private final DeviceAccessService deviceAccessService;

    @GetMapping("/{mediaEventId}")
    public ResponseEntity<DeviceMediaEventResponse> getMediaEvent(
        @RequestHeader(USER_ID_HEADER) String currentUserId,
        @PathVariable UUID mediaEventId
    ) {
        deviceAccessService.requireOwnedDeviceForMediaEvent(mediaEventId, currentUserId);
        return ResponseEntity.ok(deviceMediaService.getMediaEvent(mediaEventId));
    }
}
