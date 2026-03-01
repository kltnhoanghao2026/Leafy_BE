package com.leafy.authservice.service.jwt;

import com.leafy.authservice.dto.JwtPayload;
import com.leafy.authservice.enums.TokenType;
import com.leafy.authservice.model.User;
import com.leafy.common.enums.Role;
import com.leafy.common.utils.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * JWT Service implementation using common JwtUtil
 * Delegates to shared JwtUtil for token operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtServiceImpl implements JwtService {
    
    JwtUtil jwtUtil;
    
    @Override
    public String generateAccessToken(User user, String deviceId) {
        String sessionId = UUID.randomUUID().toString();
        return jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), sessionId, deviceId);
    }
    
    @Override
    public String generateRefreshToken(User user, String deviceId) {
        String sessionId = UUID.randomUUID().toString();
        return jwtUtil.generateRefreshToken(user.getId(), sessionId, deviceId, jwtUtil.getWebRefreshExpirationMs());
    }
    
    @Override
    public boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }
    
    @Override
    public boolean validateRefreshToken(String token) {
        try {
            // First validate signature and expiration
            if (!jwtUtil.validateToken(token)) {
                return false;
            }
            
            // Then check if it's a refresh token
            String tokenType = jwtUtil.extractTokenType(token);
            return "refresh".equals(tokenType);
        } catch (Exception e) {
            log.debug("Invalid refresh token: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public JwtPayload parseToken(String token) {
        try {
            String userId = jwtUtil.extractUserId(token);
            String email = jwtUtil.extractEmail(token);
            String roleStr = jwtUtil.extractRole(token);
            String tokenTypeStr = jwtUtil.extractTokenType(token);
            String jti = jwtUtil.extractJti(token);
            String sessionId = jwtUtil.extractSessionId(token);
            
            TokenType tokenType = "access".equals(tokenTypeStr) ? TokenType.ACCESS : TokenType.REFRESH;
            Role role = roleStr != null ? Role.valueOf(roleStr) : null;
            
            return JwtPayload.builder()
                    .subject(userId)
                    .email(email)
                    .role(role)
                    .tokenType(tokenType)
                    .jti(jti)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse JWT token: {}", e.getMessage());
            throw new RuntimeException("Invalid JWT token", e);
        }
    }
    
    @Override
    public String extractJti(String token) {
        return jwtUtil.extractJti(token);
    }
    
    @Override
    public String extractUserId(String token) {
        return jwtUtil.extractUserId(token);
    }
    
    @Override
    public long getRemainingLifetime(String token) {
        return jwtUtil.getRemainingTtl(token);
    }
}
