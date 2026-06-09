package com.leafy.authservice.model;

import com.leafy.authservice.enums.DeviceType;
import com.leafy.common.model.BaseModel;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

/**
 * Device model
 * Tracks user devices for security and session management
 */
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = false)
@Document("device")
@CompoundIndex(name = "user_device_idx", def = "{'userId': 1, 'deviceId': 1}", unique = true)
public class Device extends BaseModel {
    
    @MongoId(FieldType.OBJECT_ID)
    String id;
    
    /**
     * User ID this device belongs to
     */
    String userId;
    
    /**
     * Unique device identifier (generated on client or server)
     */
    String deviceId;
    
    /**
     * Device name/model (e.g., "iPhone 13 Pro", "Chrome on Windows")
     */
    String deviceName;
    
    /**
     * Device type (WEB, MOBILE, TABLET, DESKTOP)
     */
    DeviceType deviceType;
    
    /**
     * Platform/OS (e.g., "iOS", "Android", "Windows", "MacOS")
     */
    String platform;
    
    /**
     * Browser name (for web clients)
     */
    String browser;
    
    /**
     * Operating system version
     */
    String osVersion;
    
    /**
     * App version (for mobile apps)
     */
    String appVersion;
    
    /**
     * User agent string
     */
    String userAgent;
    
    /**
     * Last time this device was used
     */
    LocalDateTime lastUsedAt;
    
    /**
     * First time this device was registered
     */
    LocalDateTime firstSeenAt;
    
    /**
     * Whether this device is trusted
     */
    Boolean trusted;
    
    /**
     * Current refresh token JTI associated with this device
     */
    String currentRefreshTokenJti;

    /**
     * Current JWT sessionId associated with this device.
     */
    String sessionId;
}
