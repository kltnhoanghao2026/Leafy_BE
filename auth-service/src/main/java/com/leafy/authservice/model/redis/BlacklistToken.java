package com.leafy.authservice.model.redis;

import com.leafy.authservice.enums.TokenType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * BlacklistToken model
 * Tracks blacklisted tokens (both access and refresh tokens) in Redis
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode
@RedisHash("blacklist_token")
public class BlacklistToken {
    
    @Id
    String id;
    
    /**
     * JWT ID (JTI) - unique identifier for the token
     */
    @Indexed
    String jti;
    
    /**
     * User ID who owns this token
     */
    String userId;
    
    /**
     * Token type (ACCESS or REFRESH)
     */
    TokenType tokenType;
    
    /**
     * When the token expires and can be removed from blacklist
     */
    LocalDateTime expiresAt;
    
    /**
     * Time to live in seconds - Redis will automatically delete after this time
     */
    @TimeToLive
    Long ttl;
    
    /**
     * Reason for blacklisting (optional)
     */
    String reason;
}
