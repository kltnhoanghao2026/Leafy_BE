package com.leafy.authservice.service.token;

import com.leafy.authservice.enums.TokenType;
import com.leafy.authservice.model.redis.BlacklistToken;
import com.leafy.authservice.model.redis.RateLimit;
import com.leafy.authservice.repository.redis.BlacklistTokenRepository;
import com.leafy.authservice.repository.redis.RateLimitRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Token Blacklist Service implementation using Spring Data Redis
 * Manages token revocation and rate limiting using Redis Repositories
 */
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TokenBlacklistServiceImpl implements TokenBlacklistService {
    
    BlacklistTokenRepository blacklistTokenRepository;
    RateLimitRepository rateLimitRepository;
    
    @Override
    public void blacklistAccessToken(String jti, String userId, long remainingLifetime) {
        try {
            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(remainingLifetime);
            
            BlacklistToken blacklistToken = BlacklistToken.builder()
                    .jti(jti)
                    .userId(userId)
                    .tokenType(TokenType.ACCESS)
                    .expiresAt(expiresAt)
                    .ttl(remainingLifetime)  // TTL in seconds for Redis auto-expiration
                    .reason("User logout")
                    .build();
            
            blacklistTokenRepository.save(blacklistToken);
            log.info("Blacklisted access token - JTI: {}, User: {}, TTL: {}s", jti, userId, remainingLifetime);
        } catch (Exception e) {
            log.error("Failed to blacklist access token - JTI: {}", jti, e);
            throw new RuntimeException("Failed to blacklist token", e);
        }
    }
    
    @Override
    public boolean isAccessTokenBlacklisted(String jti) {
        try {
            return blacklistTokenRepository.existsByJtiAndTokenType(jti, TokenType.ACCESS);
        } catch (Exception e) {
            log.error("Failed to check access token blacklist - JTI: {}", jti, e);
            // Fail secure: if Redis is down, deny access
            return true;
        }
    }
    
    @Override
    public void blacklistRefreshToken(String jti) {
        try {
            // Refresh tokens typically have longer TTL (30 days)
            long ttlSeconds = 2592000L; // 30 days
            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ttlSeconds);
            
            BlacklistToken blacklistToken = BlacklistToken.builder()
                    .jti(jti)
                    .tokenType(TokenType.REFRESH)
                    .expiresAt(expiresAt)
                    .ttl(ttlSeconds)  // TTL in seconds for Redis auto-expiration
                    .reason("Token refresh")
                    .build();
            
            blacklistTokenRepository.save(blacklistToken);
            log.info("Blacklisted refresh token - JTI: {}, TTL: {}s", jti, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to blacklist refresh token - JTI: {}", jti, e);
            throw new RuntimeException("Failed to blacklist refresh token", e);
        }
    }
    
    @Override
    public boolean isRefreshTokenBlacklisted(String jti) {
        try {
            return blacklistTokenRepository.existsByJtiAndTokenType(jti, TokenType.REFRESH);
        } catch (Exception e) {
            log.error("Failed to check refresh token blacklist - JTI: {}", jti, e);
            // Fail secure: if Redis is down, deny access
            return true;
        }
    }
    
    @Override
    public Long incrementRateLimit(String key, long windowSeconds) {
        try {
            RateLimit rateLimit = rateLimitRepository.findByKey(key)
                    .map(existing -> {
                        existing.setCount(existing.getCount() + 1);
                        return existing;
                    })
                    .orElseGet(() -> RateLimit.builder()
                            .key(key)
                            .count(1L)
                            .ttl(windowSeconds)
                            .build());
            
            rateLimitRepository.save(rateLimit);
            log.debug("Incremented rate limit - Key: {}, Count: {}", key, rateLimit.getCount());
            return rateLimit.getCount();
        } catch (Exception e) {
            log.error("Failed to increment rate limit - Key: {}", key, e);
            // Fail open: if Redis is down, allow access (but log the error)
            return 0L;
        }
    }
    
    @Override
    public boolean isRateLimitExceeded(String key, int maxAttempts, long windowSeconds) {
        try {
            return rateLimitRepository.findByKey(key)
                    .map(rateLimit -> rateLimit.getCount() > maxAttempts)
                    .orElse(false);
        } catch (Exception e) {
            log.error("Failed to check rate limit - Key: {}", key, e);
            // Fail open: if Redis is down, allow access (but log the error)
            return false;
        }
    }
}
