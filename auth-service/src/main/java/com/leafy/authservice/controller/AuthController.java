package com.leafy.authservice.controller;

import com.leafy.authservice.dto.request.InitialRegisterRequest;
import com.leafy.authservice.dto.request.LoginRequest;
import com.leafy.authservice.dto.request.RefreshTokenRequest;
import com.leafy.authservice.dto.request.RegisterRequest;
import com.leafy.authservice.dto.request.ResendOtpRequest;
import com.leafy.authservice.dto.request.VerifyOtpRequest;
import com.leafy.authservice.dto.response.AuthResponse;
import com.leafy.authservice.dto.response.RegistrationInitResponse;
import com.leafy.authservice.enums.DeviceType;
import com.leafy.authservice.service.auth.AuthService;
import com.leafy.common.dto.ApiResponse;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication REST Controller
 * Handles authentication endpoints: registration, login, token refresh, and logout
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {
    
    AuthService authService;

    /**
     * Initiate registration endpoint (Step 1)
     * Sends OTP to email and stores registration data temporarily
     *
     * @param request the initial registration request
     * @return registration initiation response
     */
    @PostMapping("/register/init")
    public ResponseEntity<ApiResponse<RegistrationInitResponse>> initiateRegistration(
            @Valid @RequestBody InitialRegisterRequest request) {
        log.info("POST /auth/register/init - Email: {}", request.getEmail());
        
        RegistrationInitResponse response = authService.initiateRegistration(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
    
    /**
     * Verify OTP and complete registration endpoint (Step 2)
     * Verifies OTP, creates user account, and issues tokens
     *
     * @param verifyRequest the OTP verification request
     * @param userAgent User-Agent header
     * @param deviceId X-Device-ID header
     * @param httpRequest the HTTP request
     * @param response the HTTP response (for setting cookies on web clients)
     * @return authentication response with tokens
     */
    @PostMapping("/register/verify")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtpAndRegister(
            @Valid @RequestBody VerifyOtpRequest verifyRequest,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        log.info("POST /auth/register/verify - Email: {}", verifyRequest.getEmail());
        
        AuthResponse authResponse = authService.verifyOtpAndRegister(verifyRequest, userAgent, deviceId, httpRequest, response);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authResponse));
    }
    
    /**
     * Resend OTP endpoint
     * Resends OTP to email for registration verification
     *
     * @param request the resend OTP request
     * @return success message
     */
    @PostMapping("/register/resend-otp")
    public ResponseEntity<ApiResponse<String>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request) {
        log.info("POST /auth/register/resend-otp - Email: {}", request.getEmail());
        
        boolean sent = authService.resendOtp(request.getEmail());
        
        if (sent) {
            return ResponseEntity.ok(ApiResponse.success("OTP has been resent to your email"));
        } else {
           throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }
    }

    /**
     * Register endpoint (legacy - direct registration without OTP)
     * Creates a new user account and issues access and refresh tokens
     *
     * @param registerRequest the registration request
     * @param userAgent User-Agent header
     * @param deviceId X-Device-ID header
     * @param httpRequest the HTTP request
     * @param response the HTTP response (for setting cookies on web clients)
     * @return authentication response with tokens
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest registerRequest,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        log.info("POST /auth/register - Email: {}", registerRequest.getEmail());
        
        AuthResponse authResponse = authService.register(registerRequest, userAgent, deviceId, httpRequest, response);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authResponse));
    }
    
    /**
     * Login endpoint
     * Authenticates user and issues access and refresh tokens
     *
     * @param loginRequest the login request
     * @param userAgent User-Agent header
     * @param deviceId X-Device-ID header
     * @param httpRequest the HTTP request
     * @param response the HTTP response (for setting cookies on web clients)
     * @return authentication response with tokens
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        log.info("POST /auth/login - Email: {}", loginRequest.getEmail());
        
        AuthResponse authResponse = authService.login(loginRequest, userAgent, deviceId, httpRequest, response);
        
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }
    
    /**
     * Refresh token endpoint for web clients
     * Uses HttpOnly cookie to extract refresh token
     *
     * @param request the HTTP request (contains refresh token cookie)
     * @param response the HTTP response (for setting new cookie)
     * @return authentication response with new access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshTokenWeb(
            RefreshTokenRequest refreshTokenRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.debug("POST /auth/refresh - Web client");
        
        AuthResponse authResponse = authService.refreshToken(request, refreshTokenRequest, response, DeviceType.WEB);
        
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }
    
    /**
     * Refresh token endpoint for mobile clients
     * Expects refresh token in request body
     *
     * @param refreshTokenRequest the refresh token request
     * @param userAgent User-Agent header
     * @param response the HTTP response
     * @return authentication response with new access and refresh tokens
     */
    @PostMapping("/refresh/mobile")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshTokenMobile(
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.debug("POST /auth/refresh/mobile - Mobile client");
        
        // Default to MOBILE, but could parse from User-Agent if needed
        DeviceType deviceType = DeviceType.MOBILE;
        
        AuthResponse authResponse = authService.refreshToken(request, refreshTokenRequest, response, deviceType);
        
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }
    
    /**
     * Logout endpoint for web clients
     * Blacklists access token and revokes refresh token from cookie
     *
     * @param request the HTTP request (contains access token and cookie)
     * @param response the HTTP response (for clearing cookie)
     * @return no content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logoutWeb(
            HttpServletRequest request,
            HttpServletResponse response) {
        log.debug("POST /auth/logout - Web client");
        
        authService.logout(request, null, response, DeviceType.WEB);
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Logout endpoint for mobile clients
     * Blacklists access token and revokes refresh token from request body
     *
     * @param refreshTokenRequest the refresh token request
     * @param request the HTTP request (contains access token)
     * @param response the HTTP response
     * @return no content
     */
    @PostMapping("/logout/mobile")
    public ResponseEntity<Void> logoutMobile(
            @RequestBody RefreshTokenRequest refreshTokenRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.debug("POST /auth/logout/mobile - Mobile client");
        
        authService.logout(request, refreshTokenRequest, response, DeviceType.MOBILE);
        
        return ResponseEntity.noContent().build();
    }
}
