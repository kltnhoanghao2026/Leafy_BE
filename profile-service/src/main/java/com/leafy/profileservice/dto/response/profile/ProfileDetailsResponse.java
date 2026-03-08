package com.leafy.profileservice.dto.response.profile;

import com.leafy.profileservice.dto.response.preferences.UserPreferenceResponse;
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

    String certificate;

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
