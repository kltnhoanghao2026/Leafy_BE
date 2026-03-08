package com.leafy.profileservice.dto.request.profile;

import com.leafy.profileservice.dto.request.preferences.UserPreferenceRequest;
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

    String fullName;

    String profilePicture;

    String avatar;

    String certificate;

    String bio;

    UserPreferenceRequest userPreference;
}
