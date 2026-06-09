package com.leafy.authservice.repository.redis;

import com.leafy.authservice.model.redis.RateLimit;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for RateLimit entity
 * Manages rate limiting data in Redis
 */
@Repository
public interface RateLimitRepository extends CrudRepository<RateLimit, String> {
    
    /**
     * Find a rate limit entry by key
     *
     * @param key the rate limit key (e.g., user email or IP)
     * @return optional rate limit entry
     */
    Optional<RateLimit> findByKey(String key);
    
    /**
     * Check if a rate limit entry exists by key
     *
     * @param key the rate limit key
     * @return true if rate limit entry exists
     */
    boolean existsByKey(String key);
    
    /**
     * Delete a rate limit entry by key
     *
     * @param key the rate limit key
     */
    void deleteByKey(String key);
}
