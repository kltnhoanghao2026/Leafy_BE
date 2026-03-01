package com.leafy.authservice.service.auth;

import com.leafy.authservice.dto.JwtPayload;
import com.leafy.authservice.dto.request.InitialRegisterRequest;
import com.leafy.authservice.dto.request.LoginRequest;
import com.leafy.authservice.dto.request.RefreshTokenRequest;
import com.leafy.authservice.dto.request.RegisterRequest;
import com.leafy.authservice.dto.request.VerifyOtpRequest;
import com.leafy.authservice.dto.response.AuthResponse;
import com.leafy.authservice.dto.response.RegistrationInitResponse;
import com.leafy.authservice.enums.DeviceType;
import com.leafy.authservice.model.User;
import com.leafy.authservice.model.redis.RegistrationData;
import com.leafy.authservice.repository.UserRepository;
import com.leafy.authservice.repository.redis.RegistrationDataRepository;
import com.leafy.authservice.service.device.DeviceService;
import com.leafy.authservice.service.jwt.JwtService;
import com.leafy.authservice.service.otp.OtpService;
import com.leafy.authservice.service.token.TokenBlacklistService;
import com.leafy.common.config.JwtProperties;
import com.leafy.common.enums.Role;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.utils.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;

/**
 * Authentication Service implementation
 * Handles authentication flows with support for web and mobile clients
 */
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthServiceImpl implements AuthService {
    
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    JwtService jwtService;
    TokenBlacklistService blacklistService;
    DeviceService deviceService;
    JwtProperties jwtProperties;
    JwtUtil jwtUtil;
    OtpService otpService;
    RegistrationDataRepository registrationDataRepository;

    @NonFinal
    @Value("${security.rate-limit.login.max-attempts:5}")
    int loginMaxAttempts;

    @NonFinal
    @Value("${security.rate-limit.login.window:60}")
    long loginWindowSeconds;

    @NonFinal
    @Value("${security.rate-limit.refresh.max-attempts:10}")
    int refreshMaxAttempts;

    @NonFinal
    @Value("${security.rate-limit.refresh.window:60}")
    long refreshWindowSeconds;
    
    @NonFinal
    @Value("${registration.data.ttl:300}")
    long registrationDataTtl; // 5 minutes default
    
    // Cookie configuration
    static String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    static String COOKIE_PATH = "/api/v1/auth";
    static int COOKIE_MAX_AGE = 2592000; // 30 days
    
    @Override
    public RegistrationInitResponse initiateRegistration(InitialRegisterRequest request) {
        log.info("Initiating registration for email: {}", request.getEmail());
        
        // Check if user already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("Registration failed - Email already exists: {}", request.getEmail());
            throw new AppException(ErrorCode.ACC_EMAIL_ALREADY_USED);
        }
        
        if (request.getPhoneNumber() != null && userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            log.warn("Registration failed - Phone number already exists: {}", request.getPhoneNumber());
            throw new AppException(ErrorCode.ACC_PHONE_NUMBER_ALREADY_USED);
        }
        
        // Check OTP rate limit
        if (otpService.isOtpRateLimitExceeded(request.getEmail())) {
            log.warn("OTP rate limit exceeded for email: {}", request.getEmail());
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        
        // Store registration data temporarily in Redis
        RegistrationData registrationData = RegistrationData.builder()
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .hashedPassword(passwordEncoder.encode(request.getPassword()))
                .appVersion(request.getAppVersion())
                .ttl(registrationDataTtl)
                .build();
        
        registrationDataRepository.save(registrationData);
        
        // Generate and send OTP
        boolean otpSent = otpService.generateAndSendOtp(request.getEmail());
        if (!otpSent) {
            log.error("Failed to send OTP for email: {}", request.getEmail());
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }
        
        log.info("Registration initiated successfully for email: {}", request.getEmail());
        
        return RegistrationInitResponse.builder()
                .message("OTP has been sent to your email")
                .email(request.getEmail())
                .expiresInSeconds(registrationDataTtl)
                .build();
    }
    
    @Override
    public AuthResponse verifyOtpAndRegister(VerifyOtpRequest verifyRequest, String userAgent, String deviceId,
                                            HttpServletRequest httpRequest, HttpServletResponse response) {
        log.info("Verifying OTP for email: {}", verifyRequest.getEmail());
        
        // Verify OTP
        boolean isOtpValid = otpService.verifyOtp(verifyRequest.getEmail(), verifyRequest.getOtp());
        if (!isOtpValid) {
            log.warn("Invalid OTP for email: {}", verifyRequest.getEmail());
            throw new AppException(ErrorCode.OTP_INVALID);
        }
        
        // Retrieve registration data
        RegistrationData registrationData = registrationDataRepository.findByEmail(verifyRequest.getEmail())
                .orElseThrow(() -> {
                    log.error("Registration data not found for email: {}", verifyRequest.getEmail());
                    return new AppException(ErrorCode.REGISTRATION_DATA_EXPIRED);
                });
        
        // Check if user already exists (double-check)
        if (userRepository.findByEmail(registrationData.getEmail()).isPresent()) {
            log.warn("User already exists during verification: {}", registrationData.getEmail());
            registrationDataRepository.delete(registrationData);
            throw new AppException(ErrorCode.ACC_EMAIL_ALREADY_USED);
        }
        
        // Create new user
        User newUser = User.builder()
                .email(registrationData.getEmail())
                .phoneNumber(registrationData.getPhoneNumber())
                .password(registrationData.getHashedPassword())
                .role(Role.USER)
                .build();
        
        User savedUser = userRepository.save(newUser);
        log.info("User registered successfully - User ID: {}", savedUser.getId());
        
        // Clean up registration data and OTP
        registrationDataRepository.delete(registrationData);
        otpService.deleteOtp(verifyRequest.getEmail());
        
        // Register or update device
        var device = deviceService.registerOrUpdateDevice(savedUser.getId(), deviceId, userAgent, registrationData.getAppVersion());
        String finalDeviceId = device.getDeviceId();
        DeviceType deviceType = device.getDeviceType();
        
        // Generate tokens with device ID
        String accessToken = jwtService.generateAccessToken(savedUser, finalDeviceId);
        String refreshToken = jwtService.generateRefreshToken(savedUser, finalDeviceId);
        
        // Extract refresh token JTI and link to device
        String refreshTokenJti = jwtService.extractJti(refreshToken);
        deviceService.updateDeviceToken(finalDeviceId, savedUser.getId(), refreshTokenJti);
        
        log.info("Registration complete with tokens - User: {}, Device: {}, DeviceType: {}", 
                savedUser.getId(), finalDeviceId, deviceType);
        
        // Handle response based on device type
        if (deviceType == DeviceType.WEB) {
            setRefreshTokenCookie(response, refreshToken);
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                    .build();
        } else {
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                    .build();
        }
    }
    
    @Override
    public boolean resendOtp(String email) {
        log.info("Resending OTP for email: {}", email);
        
        // Check if registration data exists
        if (!registrationDataRepository.existsByEmail(email)) {
            log.warn("Registration data not found for email: {}", email);
            throw new AppException(ErrorCode.REGISTRATION_DATA_EXPIRED);
        }
        
        // Check OTP rate limit
        if (otpService.isOtpRateLimitExceeded(email)) {
            log.warn("OTP rate limit exceeded for email: {}", email);
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        
        // Delete old OTP
        otpService.deleteOtp(email);
        
        // Generate and send new OTP
        boolean otpSent = otpService.generateAndSendOtp(email);
        if (!otpSent) {
            log.error("Failed to resend OTP for email: {}", email);
            return false;
        }
        
        log.info("OTP resent successfully for email: {}", email);
        return true;
    }
    
    @Override
    public AuthResponse register(RegisterRequest request, String userAgent, String deviceId,
                                HttpServletRequest httpRequest, HttpServletResponse response) {
        log.info("Registration attempt - Email: {}", request.getEmail());
        
        // Check if user already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("Registration failed - Email already exists: {}", request.getEmail());
            throw new AppException(ErrorCode.ACC_EMAIL_ALREADY_USED);
        }
        
        if (request.getPhoneNumber() != null && userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            log.warn("Registration failed - Phone number already exists: {}", request.getPhoneNumber());
            throw new AppException(ErrorCode.ACC_PHONE_NUMBER_ALREADY_USED);
        }
        
        // Create new user
        User newUser = User.builder()
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER) // Default role for new registrations
                .build();
        
        User savedUser = userRepository.save(newUser);
        log.info("User registered successfully - User ID: {}", savedUser.getId());
        
        // Register or update device
        var device = deviceService.registerOrUpdateDevice(savedUser.getId(), deviceId, userAgent, request.getAppVersion());
        String finalDeviceId = device.getDeviceId();
        DeviceType deviceType = device.getDeviceType();
        
        // Generate tokens with device ID
        String accessToken = jwtService.generateAccessToken(savedUser, finalDeviceId);
        String refreshToken = jwtService.generateRefreshToken(savedUser, finalDeviceId);
        
        // Extract refresh token JTI and link to device
        String refreshTokenJti = jwtService.extractJti(refreshToken);
        deviceService.updateDeviceToken(finalDeviceId, savedUser.getId(), refreshTokenJti);
        
        log.info("Registration complete with tokens - User: {}, Device: {}, DeviceType: {}", 
                savedUser.getId(), finalDeviceId, deviceType);
        
        // Handle response based on device type
        if (deviceType == DeviceType.WEB) {
            setRefreshTokenCookie(response, refreshToken);
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                    .build();
        } else {
            // Mobile/Tablet/Desktop client - return both tokens
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                    .build();
        }
    }
    
    @Override
    public AuthResponse login(LoginRequest request, String userAgent, String deviceId,
                             HttpServletRequest httpRequest, HttpServletResponse response) {
        log.info("Login attempt - Email: {}", request.getEmail());
        
        // Rate limiting
        String rateLimitKey = "login:" + request.getEmail();
        if (blacklistService.isRateLimitExceeded(rateLimitKey, loginMaxAttempts, loginWindowSeconds)) {
            log.warn("Rate limit exceeded for login - Email: {}", request.getEmail());
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        blacklistService.incrementRateLimit(rateLimitKey, loginWindowSeconds);
        
        // Validate credentials
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Invalid password for user - Email: {}", request.getEmail());
            throw new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        
        // Register or update device
        var device = deviceService.registerOrUpdateDevice(user.getId(), deviceId, userAgent, request.getAppVersion());
        String finalDeviceId = device.getDeviceId();
        DeviceType deviceType = device.getDeviceType();
        
        // Generate tokens with device ID
        String accessToken = jwtService.generateAccessToken(user, finalDeviceId);
        String refreshToken = jwtService.generateRefreshToken(user, finalDeviceId);
        
        // Extract refresh token JTI and link to device
        String refreshTokenJti = jwtService.extractJti(refreshToken);
        deviceService.updateDeviceToken(finalDeviceId, user.getId(), refreshTokenJti);
        
        log.info("Login successful - User: {}, Device: {}, DeviceType: {}", user.getId(), finalDeviceId, deviceType);
        
        // Handle response based on device type
        if (deviceType == DeviceType.WEB) {
            setRefreshTokenCookie(response, refreshToken);
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                    .build();
        } else {
            // Mobile/Tablet/Desktop client - return both tokens
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                    .build();
        }
    }
    
    @Override
    public AuthResponse refreshToken(HttpServletRequest request, RefreshTokenRequest refreshTokenRequest,
                                      HttpServletResponse response, DeviceType deviceType) {
        log.debug("Token refresh attempt - DeviceType: {}", deviceType);
        
        // Extract refresh token based on device type
        String refreshToken;
        if (deviceType == DeviceType.WEB) {
            refreshToken = extractRefreshTokenFromCookie(request);
            if (refreshToken == null) {
                throw new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
            }
        } else {
            if (refreshTokenRequest == null || refreshTokenRequest.getRefreshToken() == null) {
                throw new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
            }
            refreshToken = refreshTokenRequest.getRefreshToken();
        }
        
        // Validate refresh token (including signature, expiration, and token type)
        if (!jwtService.validateRefreshToken(refreshToken)) {
            log.warn("Invalid refresh token or not a refresh token");
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        
        JwtPayload payload = jwtService.parseToken(refreshToken);
        
        // Check if refresh token is blacklisted
        if (blacklistService.isRefreshTokenBlacklisted(payload.getJti())) {
            log.warn("Attempted to use blacklisted refresh token - JTI: {}", payload.getJti());
            throw new AppException(ErrorCode.TOKEN_REVOKED);
        }
        
        // Rate limiting
        String rateLimitKey = "refresh:" + payload.getSubject();
        if (blacklistService.isRateLimitExceeded(rateLimitKey, refreshMaxAttempts, refreshWindowSeconds)) {
            log.warn("Rate limit exceeded for token refresh - User: {}", payload.getSubject());
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        blacklistService.incrementRateLimit(rateLimitKey, refreshWindowSeconds);
        
        // Get user
        User user = userRepository.findById(payload.getSubject())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Extract device ID from refresh token
        String deviceId = null;
        try {
            deviceId = jwtUtil.extractDeviceId(refreshToken);
        } catch (Exception e) {
            log.warn("No deviceId in refresh token: {}", e.getMessage());
        }

        // Update device last used
        if (deviceId != null) {
            deviceService.updateLastUsed(deviceId, user.getId());
        }
        
        // Blacklist old refresh token
        blacklistService.blacklistRefreshToken(payload.getJti());
        
        // Generate new tokens with same device ID
        String newAccessToken = jwtService.generateAccessToken(user, deviceId);
        String newRefreshToken = jwtService.generateRefreshToken(user, deviceId);
        
        // Update device with new refresh token JTI
        if (deviceId != null) {
            String newRefreshTokenJti = jwtService.extractJti(newRefreshToken);
            deviceService.updateDeviceToken(deviceId, user.getId(), newRefreshTokenJti);
        }
        
        log.info("Token refresh successful - User: {}, Device: {}", user.getId(), deviceId);
        
        // Handle response based on device type
        if (deviceType == DeviceType.WEB) {
            setRefreshTokenCookie(response, newRefreshToken);
            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                    .build();
        } else {
            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                    .build();
        }
    }
    
    @Override
    public void logout(HttpServletRequest request, RefreshTokenRequest refreshTokenRequest,
                       HttpServletResponse response, DeviceType deviceType) {
        log.debug("Logout attempt - DeviceType: {}", deviceType);
        
        try {
            // Extract and blacklist access token
            String accessToken = extractAccessToken(request);
            if (accessToken != null) {
                JwtPayload accessPayload = jwtService.parseToken(accessToken);
                long remainingLifetime = jwtService.getRemainingLifetime(accessToken);
                blacklistService.blacklistAccessToken(accessPayload.getJti(), accessPayload.getSubject(), remainingLifetime);
                log.info("Blacklisted access token - User: {}, JTI: {}", accessPayload.getSubject(), accessPayload.getJti());
            }
            
            // Extract and blacklist refresh token
            String refreshToken;
            if (deviceType == DeviceType.WEB) {
                refreshToken = extractRefreshTokenFromCookie(request);
                clearRefreshTokenCookie(response);
            } else {
                if (refreshTokenRequest != null && refreshTokenRequest.getRefreshToken() != null) {
                    refreshToken = refreshTokenRequest.getRefreshToken();
                } else {
                    refreshToken = null;
                }
            }
            
            if (refreshToken != null && jwtService.validateToken(refreshToken)) {
                JwtPayload refreshPayload = jwtService.parseToken(refreshToken);
                
                blacklistService.blacklistRefreshToken(refreshPayload.getJti());
                
                log.info("Logout successful - User: {}", refreshPayload.getSubject());
            }
        } catch (Exception e) {
            log.error("Error during logout", e);
            // Don't throw - logout should always succeed from client perspective
        }
    }
    
    /**
     * Set refresh token as HttpOnly cookie for web clients
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(COOKIE_MAX_AGE);

        response.addCookie(cookie);
    }
    
    /**
     * Clear refresh token cookie for web clients
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
    
    /**
     * Extract refresh token from cookie
     */
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        
        return Arrays.stream(request.getCookies())
                .filter(cookie -> REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Extract access token from Authorization header
     */
    private String extractAccessToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
