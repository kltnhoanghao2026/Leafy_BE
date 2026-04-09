package com.leafy.profileservice.dto.request.profile;

import com.leafy.common.enums.ProfileRole;
import com.leafy.profileservice.dto.request.preferences.UserPreferenceRequest;
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

    @NotBlank(message = "{validation.userId.required}")
    String userId;

    @NotBlank(message = "{validation.fullName.required}")
    String fullName;

    String profilePicture;

    String avatar;

    @NotNull(message = "{validation.profile.role.required}")
    ProfileRole role;

    String specialty;

    String bio;

    String addressLine;

    String provinceCode;

    String districtCode;

    String wardCode;

    Double latitude;

    Double longitude;

    @Valid
    UserPreferenceRequest userPreference;
}
