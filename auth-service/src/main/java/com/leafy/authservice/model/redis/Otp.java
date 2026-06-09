package com.leafy.authservice.model.redis;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * OTP model for storing one-time passwords in Redis
 * Used for 2-step verification during registration
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode
@RedisHash("otp")
public class Otp {
    
    @Id
    String id;
    
    /**
     * Email address for which this OTP is generated
     */
    @Indexed
    String email;
    
    /**
     * Hashed OTP code (BCrypt)
     */
    String hashedOtp;
    
    /**
     * Number of verification attempts
     */
    Integer attempts;
    
    /**
     * Maximum number of attempts allowed
     */
    Integer maxAttempts;
    
    /**
     * When the OTP was created
     */
    LocalDateTime createdAt;
    
    /**
     * Time to live in seconds (default: 5 minutes)
     */
    @TimeToLive
    Long ttl;
}
