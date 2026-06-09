package com.leafy.notificationservice.service.mail;

import com.leafy.notificationservice.dto.request.EmailRequest;
import com.leafy.notificationservice.dto.request.TemplateEmailRequest;
import com.leafy.notificationservice.dto.response.EmailResponse;

import java.util.List;

/**
 * Service interface for email operations using Brevo API
 */
public interface MailingService {

    /**
     * Send a simple email
     *
     * @param request the email request
     * @return the email response
     */
    EmailResponse sendEmail(EmailRequest request);

    /**
     * Send email using a template
     *
     * @param request the template email request
     * @return the email response
     */
    EmailResponse sendTemplateEmail(TemplateEmailRequest request);

    /**
     * Send email to a single recipient
     *
     * @param to          recipient email
     * @param subject     email subject
     * @param htmlContent HTML content
     * @return the email response
     */
    EmailResponse sendSimpleEmail(String to, String subject, String htmlContent);

    /**
     * Send email to multiple recipients
     *
     * @param toList      list of recipient emails
     * @param subject     email subject
     * @param htmlContent HTML content
     * @return the email response
     */
    EmailResponse sendBulkEmail(List<String> toList, String subject, String htmlContent);

    /**
     * Send welcome email
     *
     * @param toEmail recipient email
     * @param name    recipient name
     * @return the email response
     */
    EmailResponse sendWelcomeEmail(String toEmail, String name);

    /**
     * Send password reset email
     *
     * @param toEmail   recipient email
     * @param resetLink password reset link
     * @return the email response
     */
    EmailResponse sendPasswordResetEmail(String toEmail, String resetLink);

    /**
     * Send OTP verification email
     *
     * @param toEmail recipient email
     * @param otp     OTP code
     * @return the email response
     */
    EmailResponse sendOtpEmail(String toEmail, String otp);

    /**
     * Send notification email
     *
     * @param toEmail recipient email
     * @param subject email subject
     * @param message email message
     * @return the email response
     */
    EmailResponse sendNotificationEmail(String toEmail, String subject, String message);

    /**
     * Check if mailing service is enabled
     *
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();
}
