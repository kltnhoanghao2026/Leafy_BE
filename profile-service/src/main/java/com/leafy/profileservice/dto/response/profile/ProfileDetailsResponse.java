package com.leafy.profileservice.dto.response.profile;

import com.leafy.profileservice.dto.CertificateDto;
import com.leafy.profileservice.dto.response.preferences.UserPreferenceResponse;
import com.leafy.profileservice.model.enums.UserRole;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

/**
 * Detailed response DTO for profile information (includes audit fields)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileDetailsResponse {

    String id;

    String userId;

    String fullName;

    String profilePicture;

    String avatar;

    UserRole role;

    String specialty;

    java.util.List<CertificateDto> certificates;

    Boolean isVerified;

    String bio;

    UserPreferenceResponse userPreference;

    boolean active;

    String email;

    String phoneNumber;

    LocalDateTime createdAt;

    LocalDateTime lastModifiedAt;

    String createdBy;

    String lastModifiedBy;
}
