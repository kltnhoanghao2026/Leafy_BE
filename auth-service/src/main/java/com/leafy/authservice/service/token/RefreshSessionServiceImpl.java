package com.leafy.authservice.service.token;

import com.leafy.authservice.model.redis.RefreshSession;
import com.leafy.authservice.repository.redis.RefreshSessionRepository;
import com.leafy.common.utils.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Redis-backed refresh-session service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RefreshSessionServiceImpl implements RefreshSessionService {

    RefreshSessionRepository refreshSessionRepository;
    JwtUtil jwtUtil;

    @Override
    public void storeSession(String refreshToken, String accessTokenJti) {
        try {
            String jti = jwtUtil.extractJti(refreshToken);
            long ttlSeconds = jwtUtil.getRemainingTtl(refreshToken);
            if (ttlSeconds <= 0) {
                return;
            }

            RefreshSession refreshSession = RefreshSession.builder()
                    .jti(jti)
                    .userId(jwtUtil.extractUserId(refreshToken))
                    .deviceId(jwtUtil.extractDeviceId(refreshToken))
                    .sessionId(jwtUtil.extractSessionId(refreshToken))
                    .currentAccessJti(accessTokenJti)
                    .createdAt(LocalDateTime.now())
                    .ttl(ttlSeconds)
                    .build();

            refreshSessionRepository.save(refreshSession);
        } catch (Exception e) {
            log.error("Failed to store refresh session", e);
            throw new RuntimeException("Failed to store refresh session", e);
        }
    }

    @Override
    public boolean isSessionActive(String refreshToken) {
        try {
            String jti = jwtUtil.extractJti(refreshToken);
            String userId = jwtUtil.extractUserId(refreshToken);
            String sessionId = jwtUtil.extractSessionId(refreshToken);
            String deviceId = jwtUtil.extractDeviceId(refreshToken);

            return refreshSessionRepository.findById(jti)
                    .map(session -> userId.equals(session.getUserId())
                            && sessionId.equals(session.getSessionId())
                            && ((deviceId == null && session.getDeviceId() == null)
                            || (deviceId != null && deviceId.equals(session.getDeviceId()))))
                    .orElse(false);
        } catch (Exception e) {
            log.error("Failed to validate refresh session", e);
            return false;
        }
    }

    @Override
    public void revokeSessionByJti(String jti) {
        try {
            refreshSessionRepository.deleteById(jti);
        } catch (Exception e) {
            log.error("Failed to revoke refresh session - JTI: {}", jti, e);
        }
    }

    @Override
    public void updateAccessJti(String refreshTokenJti, String accessTokenJti) {
        try {
            refreshSessionRepository.findById(refreshTokenJti)
                    .ifPresent(session -> {
                        session.setCurrentAccessJti(accessTokenJti);
                        refreshSessionRepository.save(session);
                        log.debug("Updated access JTI for refresh session JTI: {}", refreshTokenJti);
                    });
        } catch (Exception e) {
            log.error("Failed to update access JTI for refresh session - JTI: {}", refreshTokenJti, e);
        }
    }

    @Override
    public List<RefreshSession> getSessionsByUserId(String userId) {
        try {
            return refreshSessionRepository.findByUserId(userId);
        } catch (Exception e) {
            log.error("Failed to get refresh sessions for user: {}", userId, e);
            return List.of();
        }
    }
}
