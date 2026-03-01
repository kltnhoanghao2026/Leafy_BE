package com.leafy.authservice.dto.response;

import com.leafy.authservice.enums.DeviceType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

/**
 * Device response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceResponse {
    
    String id;
    String deviceId;
    String deviceName;
    DeviceType deviceType;
    String platform;
    String browser;
    String osVersion;
    String appVersion;
    LocalDateTime lastUsedAt;
    LocalDateTime firstSeenAt;
    Boolean trusted;
    Boolean isCurrentDevice;
}
