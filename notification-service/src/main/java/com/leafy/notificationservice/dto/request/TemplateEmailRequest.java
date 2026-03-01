package com.leafy.notificationservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TemplateEmailRequest {

    @NotNull(message = "Template ID is required")
    Long templateId;

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    String toEmail;

    String toName;

    Map<String, Object> templateParams;

    String senderName;

    @Email(message = "Invalid sender email format")
    String senderEmail;
}
