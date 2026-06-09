package com.leafy.authservice.repository.redis;

import com.leafy.authservice.model.redis.RegistrationData;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for RegistrationData entity
 * Manages temporary registration data in Redis
 */
@Repository
public interface RegistrationDataRepository extends CrudRepository<RegistrationData, String> {
    
    /**
     * Find registration data by email
     *
     * @param email the email address
     * @return optional registration data
     */
    Optional<RegistrationData> findByEmail(String email);
    
    /**
     * Check if registration data exists by email
     *
     * @param email the email address
     * @return true if registration data exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Delete registration data by email
     *
     * @param email the email address
     */
    void deleteByEmail(String email);
}
