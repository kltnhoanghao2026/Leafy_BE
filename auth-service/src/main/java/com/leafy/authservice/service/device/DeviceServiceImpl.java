package com.leafy.authservice.service.device;

import com.leafy.authservice.enums.DeviceType;
import com.leafy.authservice.model.Device;
import com.leafy.authservice.repository.DeviceRepository;
import com.leafy.authservice.service.token.TokenBlacklistService;
import com.leafy.authservice.service.useragent.UserAgentService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Device Service implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceServiceImpl implements DeviceService {
    
    DeviceRepository deviceRepository;
    UserAgentService userAgentService;
    TokenBlacklistService blacklistService;
    
    @Override
    @Transactional
    public Device registerOrUpdateDevice(String userId, String deviceId, String userAgent, String appVersion) {
        // Generate device ID if not provided
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = UUID.randomUUID().toString();
            log.info("Generated new device ID: {} for user: {}", deviceId, userId);
        }
        
        // Parse user agent
        UserAgentService.ParsedUserAgent parsedUA = userAgentService.parse(userAgent);
        
        // Check if device already exists
        Device device = deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElse(null);
        
        if (device == null) {
            // NEW DEVICE: Check if user has another device of same type and logout that device
            logoutDeviceByType(userId, parsedUA.getDeviceType());
            
            // Register new device
            device = Device.builder()
                    .userId(userId)
                    .deviceId(deviceId)
                    .deviceName(parsedUA.getDeviceName())
                    .deviceType(parsedUA.getDeviceType())
                    .platform(parsedUA.getPlatform())
                    .browser(parsedUA.getBrowser())
                    .osVersion(parsedUA.getOsVersion())
                    .appVersion(appVersion)
                    .userAgent(userAgent)
                    .firstSeenAt(LocalDateTime.now())
                    .lastUsedAt(LocalDateTime.now())
                    .trusted(false)
                    .build();
            
            log.info("Registering new device: {} (type: {}) for user: {}", deviceId, parsedUA.getDeviceType(), userId);
        } else {
            // Update existing device
            device.setDeviceName(parsedUA.getDeviceName());
            device.setDeviceType(parsedUA.getDeviceType());
            device.setPlatform(parsedUA.getPlatform());
            device.setBrowser(parsedUA.getBrowser());
            device.setOsVersion(parsedUA.getOsVersion());
            device.setAppVersion(appVersion);
            device.setUserAgent(userAgent);
            device.setLastUsedAt(LocalDateTime.now());
            
            log.info("Updating existing device: {} (type: {}) for user: {}", deviceId, parsedUA.getDeviceType(), userId);
        }
        
        return deviceRepository.save(device);
    }
    
    @Override
    @Transactional
    public void updateDeviceToken(String deviceId, String userId, String jti, String sessionId) {
        deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .ifPresent(device -> {
                    device.setCurrentRefreshTokenJti(jti);
                    device.setSessionId(sessionId);
                    deviceRepository.save(device);
                    log.debug("Updated device {} token JTI/sessionId", deviceId);
                });
    }
    
    @Override
    public Device getDevice(String userId, String deviceId) {
        return deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElse(null);
    }
    
    @Override
    public List<Device> getUserDevices(String userId) {
        return deviceRepository.findByUserId(userId);
    }
    
    @Override
    @Transactional
    public void removeDevice(String userId, String deviceId) {
        deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .ifPresent(device -> {
                    // Blacklist refresh token if exists
                    if (device.getCurrentRefreshTokenJti() != null) {
                        blacklistService.blacklistRefreshToken(device.getCurrentRefreshTokenJti());
                    }
                    deviceRepository.delete(device);
                    log.info("Removed device: {} for user: {}", deviceId, userId);
                });
    }
    
    @Override
    @Transactional
    public void removeAllUserDevices(String userId) {
        List<Device> devices = deviceRepository.findByUserId(userId);
        devices.forEach(device -> {
            if (device.getCurrentRefreshTokenJti() != null) {
                blacklistService.blacklistRefreshToken(device.getCurrentRefreshTokenJti());
            }
        });
        deviceRepository.deleteByUserId(userId);
        log.info("Removed all devices for user: {}", userId);
    }
    
    @Override
    public Device findDeviceByTokenJti(String jti) {
        return deviceRepository.findByCurrentRefreshTokenJti(jti)
                .orElse(null);
    }
    
    @Override
    @Transactional
    public void updateLastUsed(String deviceId, String userId) {
        deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .ifPresent(device -> {
                    device.setLastUsedAt(LocalDateTime.now());
                    deviceRepository.save(device);
                    log.debug("Updated last used time for device: {}", deviceId);
                });
    }
    
    @Override
    @Transactional
    public void logoutDeviceByType(String userId, DeviceType deviceType) {
        List<Device> devices = deviceRepository.findByUserId(userId);
        
        devices.stream()
                .filter(device -> device.getDeviceType() == deviceType)
                .forEach(device -> {
                    // Blacklist the refresh token of the existing device
                    if (device.getCurrentRefreshTokenJti() != null) {
                        blacklistService.blacklistRefreshToken(device.getCurrentRefreshTokenJti());
                        log.info("Blacklisted token for device: {} (type: {}) due to new login of same type", 
                                device.getDeviceId(), deviceType);
                    }
                    // Remove the device
                    deviceRepository.delete(device);
                    log.info("Logged out device: {} (type: {}) for user: {}", device.getDeviceId(), deviceType, userId);
                });
    }
}
