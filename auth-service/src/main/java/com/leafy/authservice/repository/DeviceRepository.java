package com.leafy.authservice.repository;

import com.leafy.authservice.model.Device;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Device entity
 */
@Repository
public interface DeviceRepository extends MongoRepository<Device, String> {
    
    /**
     * Find device by user ID and device ID
     *
     * @param userId   User ID
     * @param deviceId Device ID
     * @return Optional Device
     */
    Optional<Device> findByUserIdAndDeviceId(String userId, String deviceId);
    
    /**
     * Find all devices for a user
     *
     * @param userId User ID
     * @return List of devices
     */
    List<Device> findByUserId(String userId);
    
    /**
     * Find device by refresh token JTI
     *
     * @param jti Refresh token JTI
     * @return Optional Device
     */
    Optional<Device> findByCurrentRefreshTokenJti(String jti);
    
    /**
     * Delete all devices for a user
     *
     * @param userId User ID
     */
    void deleteByUserId(String userId);
    
    /**
     * Count devices for a user
     *
     * @param userId User ID
     * @return Device count
     */
    long countByUserId(String userId);
}
