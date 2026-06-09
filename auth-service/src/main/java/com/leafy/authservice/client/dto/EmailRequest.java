package com.leafy.authservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Email request DTO for notification service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailRequest {
    
    /**
     * List of recipient email addresses
     */
    List<String> to;
    
    /**
     * Email subject
     */
    String subject;
    
    /**
     * HTML content of the email
     */
    String htmlContent;
    
    /**
     * Plain text content of the email
     */
    String textContent;
}
