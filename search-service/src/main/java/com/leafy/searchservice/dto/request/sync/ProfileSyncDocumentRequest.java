package com.leafy.searchservice.dto.request.sync;

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
public class ProfileSyncDocumentRequest {

    String id;

    String userId;

    String fullName;

    String profilePicture;

    String avatar;

    String phoneNumber;

    String email;

    ProfileRole role;

    String specialty;

    Boolean isVerified;

    Boolean active;

    String bio;
}