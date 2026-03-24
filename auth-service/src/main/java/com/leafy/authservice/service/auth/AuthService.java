package com.leafy.authservice.service.auth;

import com.leafy.authservice.dto.request.InitialRegisterRequest;
import com.leafy.authservice.dto.request.LoginRequest;
import com.leafy.authservice.dto.request.LogoutDeviceRequest;
import com.leafy.authservice.dto.request.RefreshTokenRequest;
import com.leafy.authservice.dto.request.RegisterRequest;
import com.leafy.authservice.dto.request.VerifyOtpRequest;
import com.leafy.authservice.dto.response.AuthResponse;
import com.leafy.authservice.dto.response.RegistrationInitResponse;
import com.leafy.authservice.enums.DeviceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authentication Service interface
 * Handles authentication flows: registration, login, token refresh, and logout
 */
public interface AuthService {
    
    /**
     * Initiate registration process (Step 1)
     * Sends OTP to email and stores registration data temporarily
     *
     * @param request the initial registration request
     * @return registration initiation response
     */
    RegistrationInitResponse initiateRegistration(InitialRegisterRequest request);
    
    /**
     * Verify OTP and complete registration (Step 2)
     * Creates user account and issues tokens
     *
     * @param verifyRequest the OTP verification request
     * @param userAgent User-Agent header
     * @param deviceId X-Device-ID header
     * @param httpRequest the HTTP request
     * @param response the HTTP response (for setting cookies on web clients)
     * @return authentication response with tokens
     */
    AuthResponse verifyOtpAndRegister(VerifyOtpRequest verifyRequest, String userAgent, String deviceId,
                                     HttpServletRequest httpRequest, HttpServletResponse response);

    /**
     * Resend OTP for registration
     *
     * @param email the email address
     * @return true if OTP was resent successfully
     */
    boolean resendOtp(String email);

    /**
     * Authenticate user and issue tokens
     *
     * @param loginRequest the login request
     * @param userAgent User-Agent header
     * @param deviceId X-Device-ID header
     * @param httpRequest the HTTP request
     * @param response the HTTP response (for setting cookies on web clients)
     * @return authentication response with tokens
     */
    AuthResponse login(LoginRequest loginRequest, String userAgent, String deviceId, 
                      HttpServletRequest httpRequest, HttpServletResponse response);
    
    /**
     * Refresh access token using refresh token
     *
     * @param request the HTTP request (contains cookie for web clients)
     * @param refreshTokenRequest the refresh token request (for mobile clients)
     * @param response the HTTP response (for setting new cookie on web clients)
     * @param deviceType the device type
     * @return authentication response with new access token
     */
    AuthResponse refreshToken(HttpServletRequest request, RefreshTokenRequest refreshTokenRequest, 
                               HttpServletResponse response, DeviceType deviceType);
    
    /**
     * Logout user and revoke tokens
     *
     * @param request the HTTP request (contains access token and cookie)
     * @param refreshTokenRequest the refresh token request (for mobile clients)
     * @param response the HTTP response (for clearing cookie on web clients)
     * @param deviceType the device type
     */
    void logout(HttpServletRequest request, RefreshTokenRequest refreshTokenRequest, 
                HttpServletResponse response, DeviceType deviceType);

    /**
     * Logout all devices for the authenticated user.
     * Revokes all refresh sessions and associated access tokens.
     *
     * @param request the HTTP request (contains access token)
     * @param response the HTTP response (for clearing cookie on web clients)
     */
    void logoutDevice(HttpServletRequest request, LogoutDeviceRequest logoutDeviceRequest, HttpServletResponse response);

    /**
     * Logout all other devices for the authenticated user.
     * Keeps the current access token session active and revokes all others.
     *
     * @param request the HTTP request (contains current access token)
     * @param response the HTTP response
     */
    void logoutOther(HttpServletRequest request, HttpServletResponse response);
}
