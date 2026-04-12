package com.leafy.authservice.service.auth;

import com.leafy.authservice.dto.JwtPayload;
import com.leafy.authservice.dto.request.InitialRegisterRequest;
import com.leafy.authservice.dto.request.LoginRequest;
import com.leafy.authservice.dto.request.LogoutDeviceRequest;
import com.leafy.authservice.dto.request.RefreshTokenRequest;
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
import com.leafy.authservice.service.token.RefreshSessionService;
import com.leafy.authservice.service.token.TokenBlacklistService;
import com.leafy.authservice.client.ProfileServiceClient;
import com.leafy.authservice.client.dto.ProfileCreateRequest;
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
    RefreshSessionService refreshSessionService;
    ProfileServiceClient profileServiceClient;

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
    static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    static final String COOKIE_PATH = "/api/auth";
    static final int COOKIE_MAX_AGE = 2592000; // 30 days

    @NonFinal
    @Value("${cookie.refresh-token.secure:false}")
    boolean cookieSecure;

    @Override
    public RegistrationInitResponse initiateRegistration(InitialRegisterRequest request) {
        log.info("Initiating registration for email: {}", request.getEmail());

        validateEmailAndPhoneAvailability(request.getEmail(), request.getPhoneNumber());
        enforceOtpRateLimit(request.getEmail());

        RegistrationData registrationData = RegistrationData.builder()
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .hashedPassword(passwordEncoder.encode(request.getPassword()))
                .appVersion(request.getAppVersion())
                .ttl(registrationDataTtl)
                .build();

        registrationDataRepository.save(registrationData);

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

        boolean isOtpValid = otpService.verifyOtp(verifyRequest.getEmail(), verifyRequest.getOtp());
        if (!isOtpValid) {
            log.warn("Invalid OTP for email: {}", verifyRequest.getEmail());
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        RegistrationData registrationData = registrationDataRepository.findByEmail(verifyRequest.getEmail())
                .orElseThrow(() -> {
                    log.error("Registration data not found for email: {}", verifyRequest.getEmail());
                    return new AppException(ErrorCode.REGISTRATION_DATA_EXPIRED);
                });

        if (userRepository.findByEmail(registrationData.getEmail()).isPresent()) {
            log.warn("User already exists during verification: {}", registrationData.getEmail());
            registrationDataRepository.delete(registrationData);
            throw new AppException(ErrorCode.ACC_EMAIL_ALREADY_USED);
        }

        User newUser = User.builder()
                .email(registrationData.getEmail())
                .phoneNumber(registrationData.getPhoneNumber())
                .password(registrationData.getHashedPassword())
                .role(Role.USER)
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("User registered successfully - User ID: {}", savedUser.getId());

        registrationDataRepository.delete(registrationData);
        otpService.deleteOtp(verifyRequest.getEmail());

        String profileId = null;
        try {
            ProfileCreateRequest profileRequest = ProfileCreateRequest.builder()
                    .userId(savedUser.getId())
                    .fullName(savedUser.getEmail().split("@")[0])
                    .role("FARMER") // match UserRole.FARMER in profile-service
                    .build();

            var profileResponse = profileServiceClient.createProfile(profileRequest);
            if (profileResponse != null && profileResponse.data() != null) {
                profileId = profileResponse.data().getId();
                log.info("Profile created synchronously for user: {}", savedUser.getId());
            }
        } catch (Exception e) {
            log.error("Failed to create profile synchronously for user {}: {}", savedUser.getId(), e.getMessage());
        }

        return authenticateAndBuildResponse(
                savedUser,
                deviceId,
                userAgent,
                registrationData.getAppVersion(),
                response,
                "Registration complete with tokens",
                profileId
        );
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
    public AuthResponse login(LoginRequest request, String userAgent, String deviceId,
                              HttpServletRequest httpRequest, HttpServletResponse response) {
        log.info("Login attempt - Email: {}", request.getEmail());

        String rateLimitKey = "login:" + request.getEmail();
        if (blacklistService.isRateLimitExceeded(rateLimitKey, loginMaxAttempts, loginWindowSeconds)) {
            log.warn("Rate limit exceeded for login - Email: {}", request.getEmail());
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        blacklistService.incrementRateLimit(rateLimitKey, loginWindowSeconds);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Invalid password for user - Email: {}", request.getEmail());
            throw new AppException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        return authenticateAndBuildResponse(
                user,
                deviceId,
                userAgent,
                request.getAppVersion(),
                response,
                "Login successful",
                null
        );
    }

    @Override
    public AuthResponse refreshToken(HttpServletRequest request, RefreshTokenRequest refreshTokenRequest,
                                     HttpServletResponse response, DeviceType deviceType) {
        log.debug("Token refresh attempt - DeviceType: {}", deviceType);

        String refreshToken = resolveRefreshToken(request, refreshTokenRequest, deviceType);

        if (!jwtService.validateRefreshToken(refreshToken)) {
            log.warn("Invalid refresh token or not a refresh token");
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        JwtPayload payload = jwtService.parseToken(refreshToken);

        if (blacklistService.isRefreshTokenBlacklisted(payload.getJti())) {
            log.warn("Attempted to use blacklisted refresh token - JTI: {}", payload.getJti());
            throw new AppException(ErrorCode.TOKEN_REVOKED);
        }

        if (!refreshSessionService.isSessionActive(refreshToken)) {
            log.warn("Refresh token session not found or mismatched - JTI: {}", payload.getJti());
            throw new AppException(ErrorCode.TOKEN_REVOKED);
        }

        String rateLimitKey = "refresh:" + payload.getSubject();
        if (blacklistService.isRateLimitExceeded(rateLimitKey, refreshMaxAttempts, refreshWindowSeconds)) {
            log.warn("Rate limit exceeded for token refresh - User: {}", payload.getSubject());
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        blacklistService.incrementRateLimit(rateLimitKey, refreshWindowSeconds);

        User user = userRepository.findById(payload.getSubject())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String deviceId = null;
        try {
            deviceId = jwtUtil.extractDeviceId(refreshToken);
        } catch (Exception e) {
            log.warn("No deviceId in refresh token: {}", e.getMessage());
        }

        if (deviceId != null) {
            deviceService.updateLastUsed(deviceId, user.getId());
        }

        blacklistService.blacklistRefreshToken(payload.getJti());

        String newAccessToken = jwtService.generateAccessToken(user, deviceId);
        String newRefreshToken = jwtService.generateRefreshToken(user, deviceId);
        String newAccessTokenJti = jwtService.extractJti(newAccessToken);
        String newRefreshTokenJti = jwtService.extractJti(newRefreshToken);

        if (deviceId != null) {
            bindRefreshTokenToDevice(deviceId, user.getId(), newRefreshToken, newAccessTokenJti);
        }

        refreshSessionService.updateAccessJti(newRefreshTokenJti, newAccessTokenJti);

        log.info("Token refresh successful - User: {}, Device: {}", user.getId(), deviceId);

        return buildAuthResponse(deviceType, response, newAccessToken, newRefreshToken);
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

            String refreshToken = null;
            if (deviceType == DeviceType.WEB) {
                refreshToken = extractRefreshTokenFromCookie(request);
                clearRefreshTokenCookie(response);
            } else {
                if (refreshTokenRequest != null && refreshTokenRequest.getRefreshToken() != null) {
                    refreshToken = refreshTokenRequest.getRefreshToken();
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

    @Override
    public void logoutDevice(HttpServletRequest request, LogoutDeviceRequest logoutDeviceRequest,
                             HttpServletResponse response) {
        log.debug("Logout specific device attempt");

        try {
            String accessToken = extractAccessToken(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                log.warn("Skip logout-device due to missing or invalid access token");
                return;
            }

            JwtPayload payload = jwtService.parseToken(accessToken);
            String targetDeviceId = logoutDeviceRequest.getDeviceId();
            if (targetDeviceId == null || targetDeviceId.isBlank()) {
                log.warn("Skip logout-device due to missing deviceId in request");
                return;
            }

            revokeDeviceSession(payload.getSubject(), targetDeviceId);

            String currentDeviceId = null;
            try {
                currentDeviceId = jwtUtil.extractDeviceId(accessToken);
            } catch (Exception ignored) {
                // Access token may not contain deviceId in older tokens.
            }
            if (currentDeviceId != null && currentDeviceId.equals(targetDeviceId)) {
                clearRefreshTokenCookie(response);
            }

            log.info("Logout device completed - User: {}, DeviceId: {}", payload.getSubject(), targetDeviceId);
        } catch (Exception e) {
            log.error("Error during logout-device", e);
        }
    }

    @Override
    public void logoutOther(HttpServletRequest request, HttpServletResponse response) {
        log.debug("Logout other devices attempt");

        try {
            String accessToken = extractAccessToken(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                log.warn("Skip logout-other due to missing or invalid access token");
                return;
            }

            JwtPayload payload = jwtService.parseToken(accessToken);
            revokeUserSessions(payload.getSubject(), payload.getJti());
            log.info("Logout other devices completed - User: {}, CurrentAccessJti: {}",
                    payload.getSubject(), payload.getJti());
        } catch (Exception e) {
            log.error("Error during logout-other", e);
        }
    }

    /**
     * Set refresh token as HttpOnly cookie for web clients
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
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
        cookie.setSecure(cookieSecure);
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

    private void revokeUserSessions(String userId, String excludedAccessJti) {
        var sessions = refreshSessionService.getSessionsByUserId(userId);
        long accessTokenTtlSeconds = Math.max(jwtProperties.getAccessTokenExpiration() / 1000, 1);

        int revokedCount = 0;
        for (var session : sessions) {
            if (excludedAccessJti != null && excludedAccessJti.equals(session.getCurrentAccessJti())) {
                continue;
            }

            if (session.getCurrentAccessJti() != null && !session.getCurrentAccessJti().isBlank()) {
                blacklistService.blacklistAccessToken(session.getCurrentAccessJti(), userId, accessTokenTtlSeconds);
            }

            blacklistService.blacklistRefreshToken(session.getJti());
            revokedCount++;
        }

        log.info("Revoked {} refresh sessions for user {}", revokedCount, userId);
    }

    private void revokeDeviceSession(String userId, String targetDeviceId) {
        var sessions = refreshSessionService.getSessionsByUserId(userId);
        long accessTokenTtlSeconds = Math.max(jwtProperties.getAccessTokenExpiration() / 1000, 1);

        int revokedCount = 0;
        for (var session : sessions) {
            if (!targetDeviceId.equals(session.getDeviceId())) {
                continue;
            }

            if (session.getCurrentAccessJti() != null && !session.getCurrentAccessJti().isBlank()) {
                blacklistService.blacklistAccessToken(session.getCurrentAccessJti(), userId, accessTokenTtlSeconds);
            }

            blacklistService.blacklistRefreshToken(session.getJti());
            revokedCount++;
        }

        log.info("Revoked {} refresh sessions for user {} on device {}", revokedCount, userId, targetDeviceId);
    }

    private void validateEmailAndPhoneAvailability(String email, String phoneNumber) {
        if (userRepository.findByEmail(email).isPresent()) {
            log.warn("Registration failed - Email already exists: {}", email);
            throw new AppException(ErrorCode.ACC_EMAIL_ALREADY_USED);
        }

        if (phoneNumber != null && userRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            log.warn("Registration failed - Phone number already exists: {}", phoneNumber);
            throw new AppException(ErrorCode.ACC_PHONE_NUMBER_ALREADY_USED);
        }
    }

    private void enforceOtpRateLimit(String email) {
        if (otpService.isOtpRateLimitExceeded(email)) {
            log.warn("OTP rate limit exceeded for email: {}", email);
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    private AuthResponse authenticateAndBuildResponse(User user,
                                                      String deviceId,
                                                      String userAgent,
                                                      String appVersion,
                                                      HttpServletResponse response,
                                                      String successLogPrefix,
                                                      String profileId) {
        var device = deviceService.registerOrUpdateDevice(user.getId(), deviceId, userAgent, appVersion);
        String finalDeviceId = device.getDeviceId();
        DeviceType deviceType = device.getDeviceType();

        String accessToken = jwtService.generateAccessToken(user, finalDeviceId, profileId);
        String refreshToken = jwtService.generateRefreshToken(user, finalDeviceId);
        String accessTokenJti = jwtService.extractJti(accessToken);

        bindRefreshTokenToDevice(finalDeviceId, user.getId(), refreshToken, accessTokenJti);

        log.info("{} - User: {}, Device: {}, DeviceType: {}",
                successLogPrefix, user.getId(), finalDeviceId, deviceType);

        return buildAuthResponse(deviceType, response, accessToken, refreshToken);
    }

    private void bindRefreshTokenToDevice(String deviceId, String userId, String refreshToken, String accessTokenJti) {
        String refreshTokenJti = jwtService.extractJti(refreshToken);
        String sessionId = jwtUtil.extractSessionId(refreshToken);
        deviceService.updateDeviceToken(deviceId, userId, refreshTokenJti, sessionId);
        refreshSessionService.storeSession(refreshToken, accessTokenJti);
    }

    private AuthResponse buildAuthResponse(DeviceType deviceType,
                                           HttpServletResponse response,
                                           String accessToken,
                                           String refreshToken) {
        if (deviceType == DeviceType.WEB) {
            setRefreshTokenCookie(response, refreshToken);
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                    .build();
        }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                .build();
    }

    private String resolveRefreshToken(HttpServletRequest request,
                                       RefreshTokenRequest refreshTokenRequest,
                                       DeviceType deviceType) {
        if (deviceType == DeviceType.WEB) {
            String refreshToken = extractRefreshTokenFromCookie(request);
            if (refreshToken == null) {
                throw new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
            }
            return refreshToken;
        }

        if (refreshTokenRequest == null || refreshTokenRequest.getRefreshToken() == null) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        return refreshTokenRequest.getRefreshToken();
    }
}