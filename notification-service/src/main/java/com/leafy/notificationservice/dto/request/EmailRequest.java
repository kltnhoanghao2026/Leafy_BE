package com.leafy.notificationservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailRequest {

    @NotEmpty(message = "At least one recipient is required")
    List<@Email(message = "Invalid email format") String> to;

    List<@Email(message = "Invalid email format") String> cc;

    List<@Email(message = "Invalid email format") String> bcc;

    @NotBlank(message = "Subject is required")
    String subject;

    @NotBlank(message = "Content is required")
    String htmlContent;

    String textContent;

    String senderName;

    @Email(message = "Invalid sender email format")
    String senderEmail;

    Map<String, String> params;

    List<AttachmentRequest> attachments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AttachmentRequest {
        String name;
        String url;
        String content; // Base64 encoded content
    }
}
