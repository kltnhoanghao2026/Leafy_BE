package com.leafy.notificationservice.service.mail;

import com.leafy.notificationservice.config.BrevoProperties;
import com.leafy.notificationservice.dto.request.EmailRequest;
import com.leafy.notificationservice.dto.request.TemplateEmailRequest;
import com.leafy.notificationservice.dto.response.EmailResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import sibApi.TransactionalEmailsApi;
import sibModel.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of MailingService using Brevo API
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "brevo.enabled", havingValue = "true", matchIfMissing = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BrevoMailingServiceImpl implements MailingService {

    TransactionalEmailsApi transactionalEmailsApi;
    BrevoProperties brevoProperties;

    @Override
    public EmailResponse sendEmail(EmailRequest request) {
        log.info("Sending email to: {}", request.getTo());
        
        try {
            SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();
            
            // Set sender
            SendSmtpEmailSender sender = new SendSmtpEmailSender();
            sender.setEmail(request.getSenderEmail() != null ? request.getSenderEmail() : brevoProperties.getDefaultSenderEmail());
            sender.setName(request.getSenderName() != null ? request.getSenderName() : brevoProperties.getDefaultSenderName());
            sendSmtpEmail.setSender(sender);
            
            // Set recipients
            List<SendSmtpEmailTo> toList = request.getTo().stream()
                    .map(email -> {
                        SendSmtpEmailTo recipient = new SendSmtpEmailTo();
                        recipient.setEmail(email);
                        return recipient;
                    })
                    .collect(Collectors.toList());
            sendSmtpEmail.setTo(toList);
            
            // Set CC if present
            if (request.getCc() != null && !request.getCc().isEmpty()) {
                List<SendSmtpEmailCc> ccList = request.getCc().stream()
                        .map(email -> {
                            SendSmtpEmailCc cc = new SendSmtpEmailCc();
                            cc.setEmail(email);
                            return cc;
                        })
                        .collect(Collectors.toList());
                sendSmtpEmail.setCc(ccList);
            }
            
            // Set BCC if present
            if (request.getBcc() != null && !request.getBcc().isEmpty()) {
                List<SendSmtpEmailBcc> bccList = request.getBcc().stream()
                        .map(email -> {
                            SendSmtpEmailBcc bcc = new SendSmtpEmailBcc();
                            bcc.setEmail(email);
                            return bcc;
                        })
                        .collect(Collectors.toList());
                sendSmtpEmail.setBcc(bccList);
            }
            
            // Set subject and content
            sendSmtpEmail.setSubject(request.getSubject());
            sendSmtpEmail.setHtmlContent(request.getHtmlContent());
            
            if (request.getTextContent() != null) {
                sendSmtpEmail.setTextContent(request.getTextContent());
            }
            
            // Set params if present
            if (request.getParams() != null && !request.getParams().isEmpty()) {
                sendSmtpEmail.setParams(request.getParams());
            }
            
            // Set attachments if present
            if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
                List<SendSmtpEmailAttachment> attachments = request.getAttachments().stream()
                        .map(att -> {
                            SendSmtpEmailAttachment attachment = new SendSmtpEmailAttachment();
                            attachment.setName(att.getName());
                            if (att.getUrl() != null) {
                                attachment.setUrl(att.getUrl());
                            }
                            if (att.getContent() != null) {
                                attachment.setContent(att.getContent().getBytes());
                            }
                            return attachment;
                        })
                        .collect(Collectors.toList());
                sendSmtpEmail.setAttachment(attachments);
            }
            
            // Send email
            CreateSmtpEmail result = transactionalEmailsApi.sendTransacEmail(sendSmtpEmail);
            
            log.info("Email sent successfully. Message ID: {}", result.getMessageId());
            
            return EmailResponse.builder()
                    .messageId(result.getMessageId())
                    .success(true)
                    .message("Email sent successfully")
                    .build();
                    
        } catch (Exception e) {
            log.error("Error sending email: {}", e.getMessage(), e);
            return EmailResponse.builder()
                    .success(false)
                    .error(e.getMessage())
                    .message("Failed to send email")
                    .build();
        }
    }

    @Override
    public EmailResponse sendTemplateEmail(TemplateEmailRequest request) {
        log.info("Sending template email (ID: {}) to: {}", request.getTemplateId(), request.getToEmail());
        
        try {
            SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();
            
            // Set sender
            SendSmtpEmailSender sender = new SendSmtpEmailSender();
            sender.setEmail(request.getSenderEmail() != null ? request.getSenderEmail() : brevoProperties.getDefaultSenderEmail());
            sender.setName(request.getSenderName() != null ? request.getSenderName() : brevoProperties.getDefaultSenderName());
            sendSmtpEmail.setSender(sender);
            
            // Set recipient
            List<SendSmtpEmailTo> toList = new ArrayList<>();
            SendSmtpEmailTo recipient = new SendSmtpEmailTo();
            recipient.setEmail(request.getToEmail());
            if (request.getToName() != null) {
                recipient.setName(request.getToName());
            }
            toList.add(recipient);
            sendSmtpEmail.setTo(toList);
            
            // Set template ID
            sendSmtpEmail.setTemplateId(request.getTemplateId());
            
            // Set template params if present
            if (request.getTemplateParams() != null && !request.getTemplateParams().isEmpty()) {
                sendSmtpEmail.setParams(request.getTemplateParams());
            }
            
            // Send email
            CreateSmtpEmail result = transactionalEmailsApi.sendTransacEmail(sendSmtpEmail);
            
            log.info("Template email sent successfully. Message ID: {}", result.getMessageId());
            
            return EmailResponse.builder()
                    .messageId(result.getMessageId())
                    .success(true)
                    .message("Template email sent successfully")
                    .build();
                    
        } catch (Exception e) {
            log.error("Error sending template email: {}", e.getMessage(), e);
            return EmailResponse.builder()
                    .success(false)
                    .error(e.getMessage())
                    .message("Failed to send template email")
                    .build();
        }
    }

    @Override
    public EmailResponse sendSimpleEmail(String to, String subject, String htmlContent) {
        EmailRequest request = EmailRequest.builder()
                .to(List.of(to))
                .subject(subject)
                .htmlContent(htmlContent)
                .build();
        return sendEmail(request);
    }

    @Override
    public EmailResponse sendBulkEmail(List<String> toList, String subject, String htmlContent) {
        EmailRequest request = EmailRequest.builder()
                .to(toList)
                .subject(subject)
                .htmlContent(htmlContent)
                .build();
        return sendEmail(request);
    }

    @Override
    public EmailResponse sendWelcomeEmail(String toEmail, String name) {
        String subject = "Welcome to Leafy!";
        Map<String, String> params = Map.of("name", name);
        String htmlContent = loadTemplateAndReplace("mailTemplates/welcome-email.html", params);
        return sendSimpleEmail(toEmail, subject, htmlContent);
    }

    @Override
    public EmailResponse sendPasswordResetEmail(String toEmail, String resetLink) {
        String subject = "Password Reset Request";
        Map<String, String> params = Map.of("resetLink", resetLink);
        String htmlContent = loadTemplateAndReplace("mailTemplates/password-reset-email.html", params);
        return sendSimpleEmail(toEmail, subject, htmlContent);
    }

    @Override
    public EmailResponse sendOtpEmail(String toEmail, String otp) {
        String subject = "Your OTP Verification Code";
        Map<String, String> params = Map.of("otp", otp);
        String htmlContent = loadTemplateAndReplace("mailTemplates/otp-email.html", params);
        return sendSimpleEmail(toEmail, subject, htmlContent);
    }

    @Override
    public EmailResponse sendNotificationEmail(String toEmail, String subject, String message) {
        Map<String, String> params = Map.of("message", message);
        String htmlContent = loadTemplateAndReplace("mailTemplates/notification-email.html", params);
        return sendSimpleEmail(toEmail, subject, htmlContent);
    }

    @Override
    public boolean isEnabled() {
        return brevoProperties.isEnabled();
    }

    // Private helper methods for loading and processing email templates

    /**
     * Load email template from resources and replace placeholders
     *
     * @param templatePath path to template file in resources
     * @param params       parameters to replace in template
     * @return processed HTML content
     */
    private String loadTemplateAndReplace(String templatePath, Map<String, String> params) {
        try {
            String template = loadTemplate(templatePath);
            return replacePlaceholders(template, params);
        } catch (Exception e) {
            log.error("Error loading template {}: {}", templatePath, e.getMessage(), e);
            return "<html><body><p>Error loading email template</p></body></html>";
        }
    }

    /**
     * Load template file from classpath
     *
     * @param templatePath path to template file
     * @return template content as string
     * @throws IOException if template cannot be loaded
     */
    private String loadTemplate(String templatePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(templatePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
    }

    /**
     * Replace placeholders in template with actual values
     *
     * @param template template string with {{placeholder}} markers
     * @param params   map of placeholder names to values
     * @return processed template
     */
    private String replacePlaceholders(String template, Map<String, String> params) {
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, entry.getValue());
        }
        return result;
    }
}
