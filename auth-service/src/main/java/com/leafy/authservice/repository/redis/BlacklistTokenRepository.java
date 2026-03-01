package com.leafy.authservice.repository.redis;

import com.leafy.authservice.enums.TokenType;
import com.leafy.authservice.model.redis.BlacklistToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for BlacklistToken entity
 * Manages blacklisted tokens in Redis
 */
@Repository
public interface BlacklistTokenRepository extends CrudRepository<BlacklistToken, String> {
    
    /**
     * Find a blacklisted token by JTI
     *
     * @param jti the JWT ID
     * @return optional blacklist token
     */
    Optional<BlacklistToken> findByJti(String jti);
    
    /**
     * Check if a token exists by JTI
     *
     * @param jti the JWT ID
     * @return true if token is blacklisted
     */
    boolean existsByJti(String jti);
    
    /**
     * Check if a token exists by JTI and token type
     *
     * @param jti the JWT ID
     * @param tokenType the token type
     * @return true if token is blacklisted
     */
    boolean existsByJtiAndTokenType(String jti, TokenType tokenType);
}
