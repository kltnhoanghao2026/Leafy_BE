package com.leafy.authservice.service.token;

/**
 * Token Blacklist Service interface
 * Manages token revocation and validation using Redis
 */
public interface TokenBlacklistService {
    
    /**
     * Blacklist an access token (typically on logout or revocation)
     *
     * @param jti the JWT ID of the token to blacklist
     * @param userId the user ID associated with the token (for audit)
     * @param remainingLifetime the remaining lifetime of the token in seconds
     */
    void blacklistAccessToken(String jti, String userId, long remainingLifetime);
    
    /**
     * Check if an access token is blacklisted
     *
     * @param jti the JWT ID of the token to check
     * @return true if blacklisted, false otherwise
     */
    boolean isAccessTokenBlacklisted(String jti);
    
    /**
     * Blacklist a refresh token (typically on logout or rotation)
     *
     * @param jti the JWT ID of the refresh token to blacklist
     */
    void blacklistRefreshToken(String jti);
    
    /**
     * Check if a refresh token is blacklisted
     *
     * @param jti the JWT ID of the refresh token to check
     * @return true if blacklisted, false otherwise
     */
    boolean isRefreshTokenBlacklisted(String jti);
    
    /**
     * Increment rate limit counter for authentication endpoints
     *
     * @param key the rate limit key (e.g., "login:192.168.1.1")
     * @param windowSeconds the time window in seconds
     * @return the current count
     */
    Long incrementRateLimit(String key, long windowSeconds);
    
    /**
     * Check if rate limit is exceeded
     *
     * @param key the rate limit key
     * @param maxAttempts the maximum allowed attempts
     * @param windowSeconds the time window in seconds
     * @return true if limit exceeded, false otherwise
     */
    boolean isRateLimitExceeded(String key, int maxAttempts, long windowSeconds);
}
