package com.leafy.profileservice.dto.response.profile;
import com.leafy.common.enums.ProfileRole;
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

    ProfileRole role;

    String specialty;

    java.util.List<CertificateDto> certificates;

    Boolean isVerified;

    String bio;

    String addressLine;

    String provinceCode;

    String districtCode;

    String wardCode;

    Double latitude;

    Double longitude;

    boolean active;

    String email;

    String phoneNumber;

    LocalDateTime createdAt;

    LocalDateTime lastModifiedAt;
}
