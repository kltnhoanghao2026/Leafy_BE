package com.leafy.searchservice.client.dto;

import com.leafy.common.enums.ProfileRole;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileServiceProfileResponse {

    String id;

    String userId;

    String fullName;

    String profilePicture;

    String avatar;

    ProfileRole role;

    String specialty;

    Boolean isVerified;

    Boolean active;

    String bio;
}
