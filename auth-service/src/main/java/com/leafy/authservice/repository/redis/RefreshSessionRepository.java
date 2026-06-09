package com.leafy.authservice.repository.redis;

import com.leafy.authservice.model.redis.RefreshSession;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for active refresh-token sessions in Redis.
 */
@Repository
public interface RefreshSessionRepository extends CrudRepository<RefreshSession, String> {

    List<RefreshSession> findByUserId(String userId);

    Optional<RefreshSession> findByUserIdAndDeviceId(String userId, String deviceId);

    void deleteByUserIdAndDeviceId(String userId, String deviceId);
}
