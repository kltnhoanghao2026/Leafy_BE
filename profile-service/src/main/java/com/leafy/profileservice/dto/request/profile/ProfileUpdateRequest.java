package com.leafy.profileservice.dto.request.profile;

import com.leafy.common.enums.ProfileRole;
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

    String avatar;

    ProfileRole role;

    String specialty;

    String bio;

    String fullName;

    String addressLine;

    String provinceCode;

    String districtCode;

    String wardCode;

    Double latitude;

    Double longitude;

    UserPreferenceRequest userPreference;
}
