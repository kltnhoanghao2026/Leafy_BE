package com.leafy.authservice.service.token;

import com.leafy.authservice.model.redis.RefreshSession;

import java.util.List;

/**
 * Manages active refresh-token sessions persisted in Redis.
 */
public interface RefreshSessionService {

    void storeSession(String refreshToken, String accessTokenJti);

    boolean isSessionActive(String refreshToken);

    void revokeSessionByJti(String jti);

    void updateAccessJti(String refreshTokenJti, String accessTokenJti);

    List<RefreshSession> getSessionsByUserId(String userId);
}
