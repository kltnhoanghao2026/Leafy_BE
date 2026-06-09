package com.leafy.searchservice.dto.response;

import com.leafy.common.enums.ProfileRole;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ProfileResponse {
    String id;
    String userId;
    String fullName;
    String profilePicture;
    String avatar;
    ProfileRole role;
    String specialty;
    Boolean isVerified;
    String bio;
    String addressLine;
    String provinceCode;
    String districtCode;
    String wardCode;
    Double latitude;
    Double longitude;
}
