package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaEventResponse;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/iot/media-events")
@RequiredArgsConstructor
public class DeviceMediaController {

    private final DeviceMediaService deviceMediaService;

    @GetMapping("/{mediaEventId}")
    public ResponseEntity<DeviceMediaEventResponse> getMediaEvent(@PathVariable UUID mediaEventId) {
        return ResponseEntity.ok(deviceMediaService.getMediaEvent(mediaEventId));
    }
}
