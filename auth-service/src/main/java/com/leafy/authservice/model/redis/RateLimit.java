package com.leafy.authservice.model.redis;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

/**
 * RateLimit model
 * Tracks rate limiting for authentication attempts in Redis
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode
@RedisHash("rate_limit")
public class RateLimit {
    
    @Id
    String id;
    
    /**
     * Rate limit key (e.g., user email or IP address)
     */
    @Indexed
    String key;
    
    /**
     * Attempt count within the current time window
     */
    Long count;
    
    /**
     * Time to live in seconds - Redis will automatically delete after this time
     */
    @TimeToLive
    Long ttl;
}
