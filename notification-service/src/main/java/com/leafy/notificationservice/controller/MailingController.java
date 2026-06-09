package com.leafy.notificationservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.notificationservice.dto.request.EmailRequest;
import com.leafy.notificationservice.dto.request.SendBulkEmailRequest;
import com.leafy.notificationservice.dto.request.SendNotificationEmailRequest;
import com.leafy.notificationservice.dto.request.SendOtpRequest;
import com.leafy.notificationservice.dto.request.SendPasswordResetRequest;
import com.leafy.notificationservice.dto.request.SendSimpleEmailRequest;
import com.leafy.notificationservice.dto.request.SendWelcomeRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/mailing")
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MailingController {

    MailingService mailingService;

    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendEmail(@Valid @RequestBody EmailRequest request) {
        EmailResponse response = mailingService.sendEmail(request);
        if (!response.isSuccess()) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED, response.getError());
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/send-template")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendTemplateEmail(@Valid @RequestBody TemplateEmailRequest request) {
        EmailResponse response = mailingService.sendTemplateEmail(request);
        if (!response.isSuccess()) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED, response.getError());
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/send-simple")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendSimpleEmail(@Valid @RequestBody SendSimpleEmailRequest request) {
        EmailResponse response = mailingService.sendSimpleEmail(request.getTo(), request.getSubject(), request.getHtmlContent());
        if (!response.isSuccess()) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED, response.getError());
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/send-bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendBulkEmail(@Valid @RequestBody SendBulkEmailRequest request) {
        EmailResponse response = mailingService.sendBulkEmail(request.getToList(), request.getSubject(), request.getHtmlContent());
        if (!response.isSuccess()) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED, response.getError());
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/send-welcome")
    @PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendWelcomeEmail(@Valid @RequestBody SendWelcomeRequest request) {
        EmailResponse response = mailingService.sendWelcomeEmail(request.getToEmail(), request.getName());
        if (!response.isSuccess()) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED, response.getError());
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/send-password-reset")
    @PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendPasswordResetEmail(@Valid @RequestBody SendPasswordResetRequest request) {
        EmailResponse response = mailingService.sendPasswordResetEmail(request.getToEmail(), request.getResetLink());
        if (!response.isSuccess()) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED, response.getError());
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/send-otp")
    @PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendOtpEmail(@Valid @RequestBody SendOtpRequest request) {
        EmailResponse response = mailingService.sendOtpEmail(request.getToEmail(), request.getOtp());
        if (!response.isSuccess()) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED, response.getError());
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/send-notification")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<EmailResponse>> sendNotificationEmail(@Valid @RequestBody SendNotificationEmailRequest request) {
        EmailResponse response = mailingService.sendNotificationEmail(request.getToEmail(), request.getSubject(), request.getMessage());
        if (!response.isSuccess()) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED, response.getError());
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        Map<String, Object> status = Map.of(
                "enabled", mailingService.isEnabled(),
                "service", "Brevo",
                "status", mailingService.isEnabled() ? "active" : "inactive"
        );
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Mailing service is healthy"));
    }
}
