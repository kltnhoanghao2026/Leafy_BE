package com.leafy.profileservice.dto.response.profile;

import com.leafy.profileservice.dto.response.preferences.UserPreferenceResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

/**
 * Response DTO for profile information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileResponse {

    String id;

    String userId;

    String fullName;

    String profilePicture;

    String avatar;

    String certificate;

    String bio;

    boolean active;

    String email;

    String phoneNumber;

    LocalDateTime createdAt;

    LocalDateTime lastModifiedAt;
}
