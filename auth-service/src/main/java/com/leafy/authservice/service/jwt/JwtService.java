package com.leafy.authservice.service.jwt;

import com.leafy.authservice.dto.JwtPayload;
import com.leafy.authservice.model.User;

/**
 * JWT Service interface
 * Handles JWT token generation, validation, and parsing
 */
public interface JwtService {
    
    /**
     * Generate an access token for the given user
     *
     * @param user the user for whom to generate the token
     * @param deviceId the device ID
     * @return the generated JWT access token
     */
    String generateAccessToken(User user, String deviceId);
    
    /**
     * Generate a refresh token for the given user
     *
     * @param user the user for whom to generate the token
     * @param deviceId the device ID
     * @return the generated JWT refresh token
     */
    String generateRefreshToken(User user, String deviceId);
    
    /**
     * Validate a JWT token
     * Checks signature, expiration, and structure
     *
     * @param token the JWT token to validate
     * @return true if valid, false otherwise
     */
    boolean validateToken(String token);
    
    /**
     * Validate a refresh token
     * Checks signature, expiration, structure, and token type
     *
     * @param token the JWT refresh token to validate
     * @return true if valid and is a refresh token, false otherwise
     */
    boolean validateRefreshToken(String token);
    
    /**
     * Parse and extract payload from a JWT token
     * Does not validate the token - use validateToken() first
     *
     * @param token the JWT token to parse
     * @return the extracted payload
     * @throws RuntimeException if token is malformed
     */
    JwtPayload parseToken(String token);
    
    /**
     * Extract the JWT ID (jti) from a token
     *
     * @param token the JWT token
     * @return the JWT ID
     */
    String extractJti(String token);
    
    /**
     * Extract the user ID (subject) from a token
     *
     * @param token the JWT token
     * @return the user ID
     */
    String extractUserId(String token);
    
    /**
     * Calculate the remaining lifetime of a token in seconds
     *
     * @param token the JWT token
     * @return remaining lifetime in seconds, or 0 if expired
     */
    long getRemainingLifetime(String token);
}
