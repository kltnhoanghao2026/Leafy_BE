package com.leafy.authservice.dto;

import com.leafy.authservice.enums.TokenType;
import com.leafy.common.enums.Role;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;

/**
 * JWT payload data transfer object
 * Represents the claims stored in a JWT token
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwtPayload {
    
    /**
     * Subject - User ID
     */
    String subject;
    
    /**
     * User email address
     */
    String email;
    
    /**
     * User role (only for access tokens)
     */
    Role role;
    
    /**
     * Token type (ACCESS or REFRESH)
     */
    TokenType tokenType;
    
    /**
     * JWT ID - unique identifier for this token
     */
    String jti;
    
    /**
     * Token version within family (only for refresh tokens)
     */
    Integer version;
    
    /**
     * Issued at timestamp
     */
    Date issuedAt;
    
    /**
     * Expiration timestamp
     */
    Date expiresAt;
    
    /**
     * Issuer identifier
     */
    String issuer;
    
    /**
     * Audience (intended recipients)
     */
    String audience;
}
