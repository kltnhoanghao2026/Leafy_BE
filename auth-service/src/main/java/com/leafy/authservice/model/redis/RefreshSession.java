package com.leafy.authservice.model.redis;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * Active refresh-token session stored in Redis.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode
@RedisHash("refresh_session")
public class RefreshSession {

    /**
     * JWT ID (JTI) of refresh token.
     */
    @Id
    String jti;

    @Indexed
    String userId;

    @Indexed
    String deviceId;

    @Indexed
    String sessionId;

    /**
     * Current access token JTI associated with this refresh session.
     */
    String currentAccessJti;

    LocalDateTime createdAt;

    /**
     * Time to live in seconds.
     */
    @TimeToLive
    Long ttl;
}
