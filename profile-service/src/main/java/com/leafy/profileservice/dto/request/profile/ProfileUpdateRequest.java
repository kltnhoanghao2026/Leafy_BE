package com.leafy.profileservice.dto.request.profile;

import com.leafy.profileservice.dto.request.preferences.UserPreferenceRequest;
import com.leafy.profileservice.model.enums.UserRole;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Request DTO for updating an existing profile
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileUpdateRequest {

    String avatar;

    UserRole role;

    String specialty;

    String bio;

    UserPreferenceRequest userPreference;
}
