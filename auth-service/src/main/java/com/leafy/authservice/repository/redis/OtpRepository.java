package com.leafy.authservice.repository.redis;

import com.leafy.authservice.model.redis.Otp;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for OTP entity
 * Manages OTP data in Redis
 */
@Repository
public interface OtpRepository extends CrudRepository<Otp, String> {
    
    /**
     * Find an OTP entry by email
     *
     * @param email the email address
     * @return optional OTP entry
     */
    Optional<Otp> findByEmail(String email);
    
    /**
     * Check if an OTP entry exists by email
     *
     * @param email the email address
     * @return true if OTP entry exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Delete an OTP entry by email
     *
     * @param email the email address
     */
    void deleteByEmail(String email);
}
