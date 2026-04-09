package com.leafy.authservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Request DTO for creating a profile in profile-service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateProfileRequest {
    String userId;
    String fullName;
    String email;
    String phoneNumber;
    String role;
    String specialty;
    String bio;
    String addressLine;
    String provinceCode;
    String districtCode;
    String wardCode;
    Double latitude;
    Double longitude;
}
