package com.leafy.authservice.service.otp;

import com.leafy.authservice.client.NotificationClient;
import com.leafy.authservice.client.dto.EmailRequest;
import com.leafy.authservice.model.redis.Otp;
import com.leafy.authservice.repository.redis.OtpRepository;
import com.leafy.authservice.service.token.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * OTP Service implementation
 * Handles OTP generation, validation, and rate limiting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {
    
    private final OtpRepository otpRepository;
    private final NotificationClient notificationClient;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;
    
    @Value("${otp.length:6}")
    private int otpLength;
    
    @Value("${otp.ttl:300}")
    private long otpTtl; // 5 minutes default
    
    @Value("${otp.max-attempts:3}")
    private int maxAttempts;
    
    @Value("${otp.rate-limit.max-requests:3}")
    private int otpRateLimitMaxRequests;
    
    @Value("${otp.rate-limit.window-seconds:300}")
    private long otpRateLimitWindowSeconds;
    
    private static final SecureRandom RANDOM = new SecureRandom();
    
    @Override
    public boolean generateAndSendOtp(String email) {
        try {
            // Check rate limit
            if (isOtpRateLimitExceeded(email)) {
                log.warn("OTP rate limit exceeded for email: {}", email);
                return false;
            }
            
            // Generate OTP
            String otpCode = generateOtpCode();
            String hashedOtp = passwordEncoder.encode(otpCode);
            
            // Store OTP in Redis
            Otp otp = Otp.builder()
                    .email(email)
                    .hashedOtp(hashedOtp)
                    .attempts(0)
                    .maxAttempts(maxAttempts)
                    .createdAt(LocalDateTime.now())
                    .ttl(otpTtl)
                    .build();
            
            otpRepository.save(otp);
            
            // Increment rate limit
            tokenBlacklistService.incrementRateLimit("otp:" + email, otpRateLimitWindowSeconds);
            
            // Send OTP via email
            sendOtpEmail(email, otpCode);
            
            log.info("OTP generated and sent to email: {}", email);
            return true;
        } catch (Exception e) {
            log.error("Failed to generate and send OTP for email: {}", email, e);
            return false;
        }
    }
    
    @Override
    public boolean verifyOtp(String email, String otpCode) {
        try {
            Otp otp = otpRepository.findByEmail(email).orElse(null);
            
            if (otp == null) {
                log.warn("No OTP found for email: {}", email);
                return false;
            }
            
            // Check if max attempts exceeded
            if (otp.getAttempts() >= otp.getMaxAttempts()) {
                log.warn("Max OTP verification attempts exceeded for email: {}", email);
                otpRepository.delete(otp);
                return false;
            }
            
            // Verify OTP
            boolean isValid = passwordEncoder.matches(otpCode, otp.getHashedOtp());
            
            if (isValid) {
                log.info("OTP verified successfully for email: {}", email);
                otpRepository.delete(otp);
                return true;
            } else {
                // Increment attempt count
                otp.setAttempts(otp.getAttempts() + 1);
                otpRepository.save(otp);
                log.warn("Invalid OTP attempt for email: {} (attempt {}/{})", email, otp.getAttempts(), otp.getMaxAttempts());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to verify OTP for email: {}", email, e);
            return false;
        }
    }
    
    @Override
    public boolean isOtpRateLimitExceeded(String email) {
        String rateLimitKey = "otp:" + email;
        return tokenBlacklistService.isRateLimitExceeded(
                rateLimitKey, 
                otpRateLimitMaxRequests, 
                otpRateLimitWindowSeconds
        );
    }
    
    @Override
    public void deleteOtp(String email) {
        otpRepository.deleteByEmail(email);
        log.info("OTP deleted for email: {}", email);
    }
    
    /**
     * Generate random OTP code
     */
    private String generateOtpCode() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(RANDOM.nextInt(10));
        }
        return otp.toString();
    }
    
    /**
     * Send OTP via email using notification service
     */
    private void sendOtpEmail(String email, String otpCode) {
        try {
            String subject = "Your Verification Code";
            String htmlContent = buildOtpEmailBody(otpCode);
            
            EmailRequest emailRequest = EmailRequest.builder()
                    .to(List.of(email))
                    .subject(subject)
                    .htmlContent(htmlContent)
                    .build();
            
            notificationClient.sendEmail(emailRequest);
            log.info("OTP email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to: {}", email, e);
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }
    
    /**
     * Build HTML email body for OTP
     */
    private String buildOtpEmailBody(String otpCode) {
        return String.format("""
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                        <h2 style="color: #4CAF50;">Email Verification</h2>
                        <p>Thank you for registering with Leafy!</p>
                        <p>Your verification code is:</p>
                        <div style="background-color: #f5f5f5; padding: 20px; text-align: center; border-radius: 5px; margin: 20px 0;">
                            <h1 style="color: #333; letter-spacing: 5px; margin: 0;">%s</h1>
                        </div>
                        <p>This code will expire in 5 minutes.</p>
                        <p>If you didn't request this code, please ignore this email.</p>
                        <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                        <p style="color: #999; font-size: 12px;">This is an automated email, please do not reply.</p>
                    </div>
                </body>
                </html>
                """, otpCode);
    }
}
