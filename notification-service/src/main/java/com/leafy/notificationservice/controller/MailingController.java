package com.leafy.notificationservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.notificationservice.dto.request.EmailRequest;
import com.leafy.notificationservice.dto.request.TemplateEmailRequest;
import com.leafy.notificationservice.dto.response.EmailResponse;
import com.leafy.notificationservice.service.mail.MailingService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for email operations
 */
@RestController
@RequestMapping("/mailing")
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MailingController {

    MailingService mailingService;

    /**
     * Send an email
     *
     * @param request the email request
     * @return the email response
     */
    @PostMapping("/send")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendEmail(@Valid @RequestBody EmailRequest request) {
        log.info("POST /api/v1/mailing/send - Sending email");
        EmailResponse response = mailingService.sendEmail(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(5001, response.getMessage(), null));
        }
    }

    /**
     * Send a template-based email
     *
     * @param request the template email request
     * @return the email response
     */
    @PostMapping("/send-template")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendTemplateEmail(@Valid @RequestBody TemplateEmailRequest request) {
        log.info("POST /api/v1/mailing/send-template - Sending template email");
        EmailResponse response = mailingService.sendTemplateEmail(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(5002, response.getMessage(), null));
        }
    }

    /**
     * Send a simple email
     *
     * @param requestBody contains to, subject, and htmlContent
     * @return the email response
     */
    @PostMapping("/send-simple")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendSimpleEmail(@RequestBody Map<String, String> requestBody) {
        log.info("POST /api/v1/mailing/send-simple - Sending simple email");
        
        String to = requestBody.get("to");
        String subject = requestBody.get("subject");
        String htmlContent = requestBody.get("htmlContent");
        
        EmailResponse response = mailingService.sendSimpleEmail(to, subject, htmlContent);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(5003, response.getMessage(), null));
        }
    }

    /**
     * Send bulk email to multiple recipients
     *
     * @param requestBody contains toList, subject, and htmlContent
     * @return the email response
     */
    @PostMapping("/send-bulk")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendBulkEmail(@RequestBody Map<String, Object> requestBody) {
        log.info("POST /api/v1/mailing/send-bulk - Sending bulk email");
        
        @SuppressWarnings("unchecked")
        List<String> toList = (List<String>) requestBody.get("toList");
        String subject = (String) requestBody.get("subject");
        String htmlContent = (String) requestBody.get("htmlContent");
        
        EmailResponse response = mailingService.sendBulkEmail(toList, subject, htmlContent);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(5004, response.getMessage(), null));
        }
    }

    /**
     * Send welcome email
     *
     * @param requestBody contains toEmail and name
     * @return the email response
     */
    @PostMapping("/send-welcome")
    public ResponseEntity<ApiResponse<EmailResponse>> sendWelcomeEmail(@RequestBody Map<String, String> requestBody) {
        log.info("POST /api/v1/mailing/send-welcome - Sending welcome email");
        
        String toEmail = requestBody.get("toEmail");
        String name = requestBody.get("name");
        
        EmailResponse response = mailingService.sendWelcomeEmail(toEmail, name);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(5005, response.getMessage(), null));
        }
    }

    /**
     * Send password reset email
     *
     * @param requestBody contains toEmail and resetLink
     * @return the email response
     */
    @PostMapping("/send-password-reset")
    public ResponseEntity<ApiResponse<EmailResponse>> sendPasswordResetEmail(@RequestBody Map<String, String> requestBody) {
        log.info("POST /api/v1/mailing/send-password-reset - Sending password reset email");
        
        String toEmail = requestBody.get("toEmail");
        String resetLink = requestBody.get("resetLink");
        
        EmailResponse response = mailingService.sendPasswordResetEmail(toEmail, resetLink);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(5006, response.getMessage(), null));
        }
    }

    /**
     * Send OTP verification email
     *
     * @param requestBody contains toEmail and otp
     * @return the email response
     */
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<EmailResponse>> sendOtpEmail(@RequestBody Map<String, String> requestBody) {
        log.info("POST /api/v1/mailing/send-otp - Sending OTP email");
        
        String toEmail = requestBody.get("toEmail");
        String otp = requestBody.get("otp");
        
        EmailResponse response = mailingService.sendOtpEmail(toEmail, otp);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(5007, response.getMessage(), null));
        }
    }

    /**
     * Send notification email
     *
     * @param requestBody contains toEmail, subject, and message
     * @return the email response
     */
    @PostMapping("/send-notification")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendNotificationEmail(@RequestBody Map<String, String> requestBody) {
        log.info("POST /api/v1/mailing/send-notification - Sending notification email");
        
        String toEmail = requestBody.get("toEmail");
        String subject = requestBody.get("subject");
        String message = requestBody.get("message");
        
        EmailResponse response = mailingService.sendNotificationEmail(toEmail, subject, message);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(5008, response.getMessage(), null));
        }
    }

    /**
     * Check mailing service status
     *
     * @return service status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        log.info("GET /api/v1/mailing/status - Checking mailing service status");
        
        Map<String, Object> status = Map.of(
                "enabled", mailingService.isEnabled(),
                "service", "Brevo",
                "status", mailingService.isEnabled() ? "active" : "inactive"
        );
        
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * Health check endpoint
     *
     * @return health status
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Mailing service is healthy"));
    }
}
