package com.leafy.authservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Authentication response DTO
 * Contains access token and optionally refresh token (for mobile clients)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    
    /**
     * Access token (JWT)
     */
    String accessToken;
    
    /**
     * Refresh token (JWT) - only returned for mobile clients
     */
    String refreshToken;
    
    /**
     * Token type (always "Bearer")
     */
    @Builder.Default
    String tokenType = "Bearer";
    
    /**
     * Access token expiration in seconds
     */
    Long expiresIn;

}
