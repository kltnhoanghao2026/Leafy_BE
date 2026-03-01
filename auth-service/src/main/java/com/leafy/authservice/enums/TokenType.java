package com.leafy.authservice.enums;

/**
 * Token type enumeration
 * Distinguishes between access and refresh tokens
 */
public enum TokenType {
    /**
     * Short-lived access token for API authorization
     */
    ACCESS,
    
    /**
     * Long-lived refresh token for obtaining new access tokens
     */
    REFRESH
}
