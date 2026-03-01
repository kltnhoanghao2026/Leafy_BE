package com.leafy.authservice.model.redis;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

/**
 * Temporary registration data stored in Redis
 * Holds user registration information until OTP is verified
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode
@RedisHash("registration_data")
public class RegistrationData {
    
    @Id
    String id;
    
    /**
     * Email address (used as lookup key)
     */
    @Indexed
    String email;
    
    /**
     * Phone number
     */
    String phoneNumber;
    
    /**
     * Hashed password
     */
    String hashedPassword;
    
    /**
     * App version (for mobile apps)
     */
    String appVersion;
    
    /**
     * Time to live in seconds (default: 5 minutes = 300 seconds)
     */
    @TimeToLive
    Long ttl;
}
