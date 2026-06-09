package com.leafy.profileservice.dto.response.profile;

import com.leafy.common.enums.ProfileRole;

/**
 * Embedded user/profile info attached to an ApprovalRequestResponse.
 */
public record ApprovalRequestUserInfo(
        String fullName,
        String avatar,
        String profilePicture,
        ProfileRole role,
        String email,
        Boolean isVerified
) {
}
