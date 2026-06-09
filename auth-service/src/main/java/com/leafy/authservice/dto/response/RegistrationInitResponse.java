package com.leafy.authservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Registration initiation response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegistrationInitResponse {
    
    String message;
    String email;
    Long expiresInSeconds;
}
