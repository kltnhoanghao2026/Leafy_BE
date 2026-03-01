package com.leafy.authservice.service.otp;

/**
 * OTP Service interface
 * Handles OTP generation, validation, and management
 */
public interface OtpService {
    
    /**
     * Generate and send OTP to email
     * Stores hashed OTP in Redis with TTL
     *
     * @param email the email address
     * @return true if OTP was sent successfully
     */
    boolean generateAndSendOtp(String email);
    
    /**
     * Verify OTP code
     *
     * @param email the email address
     * @param otpCode the OTP code to verify
     * @return true if OTP is valid
     */
    boolean verifyOtp(String email, String otpCode);
    
    /**
     * Check if rate limit is exceeded for OTP sending
     *
     * @param email the email address
     * @return true if rate limit is exceeded
     */
    boolean isOtpRateLimitExceeded(String email);
    
    /**
     * Delete OTP for email
     *
     * @param email the email address
     */
    void deleteOtp(String email);
}
