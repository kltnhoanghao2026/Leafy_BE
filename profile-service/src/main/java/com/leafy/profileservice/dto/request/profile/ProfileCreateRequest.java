package com.leafy.profileservice.dto.request.profile;

import com.leafy.profileservice.dto.request.preferences.UserPreferenceRequest;
import com.leafy.profileservice.model.enums.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Role is required")
    UserRole role;

    String specialty;

    String bio;

    @Valid
    UserPreferenceRequest userPreference;
}
