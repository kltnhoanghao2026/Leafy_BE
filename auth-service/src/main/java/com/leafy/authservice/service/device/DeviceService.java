package com.leafy.authservice.service.device;

import com.leafy.authservice.enums.DeviceType;
import com.leafy.authservice.model.Device;

import java.util.List;

/**
 * Device Service interface
 * Manages user devices for security and session tracking
 */
public interface DeviceService {
    
    /**
     * Register or update a device for a user
     *
     * @param userId      User ID
     * @param deviceId    Device ID (from header)
     * @param userAgent   User agent string
     * @param appVersion  App version (optional, for mobile)
     * @return Registered or updated Device
     */
    Device registerOrUpdateDevice(String userId, String deviceId, String userAgent, String appVersion);
    
    /**
     * Update device with refresh token JTI
     *
     * @param deviceId Device ID
     * @param userId   User ID
     * @param jti      Refresh token JTI
        * @param sessionId JWT session ID
     */
        void updateDeviceToken(String deviceId, String userId, String jti, String sessionId);
    
    /**
     * Get device by user ID and device ID
     *
     * @param userId   User ID
     * @param deviceId Device ID
     * @return Device or null
     */
    Device getDevice(String userId, String deviceId);
    
    /**
     * Get all devices for a user
     *
     * @param userId User ID
     * @return List of devices
     */
    List<Device> getUserDevices(String userId);
    
    /**
     * Remove a device
     *
     * @param userId   User ID
     * @param deviceId Device ID
     */
    void removeDevice(String userId, String deviceId);
    
    /**
     * Remove all devices for a user
     *
     * @param userId User ID
     */
    void removeAllUserDevices(String userId);
    
    /**
     * Find device by refresh token JTI
     *
     * @param jti Refresh token JTI
     * @return Device or null
     */
    Device findDeviceByTokenJti(String jti);
    
    /**
     * Update device last used timestamp
     *
     * @param deviceId Device ID
     * @param userId   User ID
     */
    void updateLastUsed(String deviceId, String userId);
    
    /**
     * Logout device of specific type (used to enforce single device per type)
     *
     * @param userId     User ID
     * @param deviceType Device type to logout
     */
    void logoutDeviceByType(String userId, DeviceType deviceType);
}
