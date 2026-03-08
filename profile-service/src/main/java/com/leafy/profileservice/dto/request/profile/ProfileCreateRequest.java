package com.leafy.profileservice.dto.request.profile;

import com.leafy.profileservice.dto.request.preferences.UserPreferenceRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Request DTO for creating a new profile
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileCreateRequest {

    @NotBlank(message = "User ID is required")
    String userId;

    @NotBlank(message = "Full name is required")
    String fullName;

    String profilePicture;

    String avatar;

    String certificate;

    String bio;

    UserPreferenceRequest userPreference;
}
