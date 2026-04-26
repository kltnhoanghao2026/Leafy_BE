package com.leafy.profileservice.model;

import com.leafy.common.enums.ProfileRole;
import com.leafy.common.model.BaseModel;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * Profile model
 * Stores user profile information including personal details and preferences
 */
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = false)
@Document("profile")
@NoArgsConstructor
@AllArgsConstructor
public class Profile extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    /**
     * User ID from auth service (references User entity)
     */
    @Indexed(unique = true)
    String userId;

    /**
     * User's full name
     */
    String fullName;

    /**
     * URL to a user's profile picture
     */
    String profilePicture;

    /**
     * File ID of the user's avatar referencing file-service
     */
    String avatar;

    /**
     * User's primary role in the platform
     */
    ProfileRole role;

    /**
     * User's area of specialty (e.g., crop types, soil analysis)
     */
    String specialty;

    /**
     * Flag indicating if the user has been verified by an admin
     */
    @Builder.Default
    Boolean isVerified = false;

    /**
     * User's biography or description
     */
    String bio;

    /**
     * User's address line
     */
    String addressLine;

    /**
     * Province code
     */
    String provinceCode;

    /**
     * District code
     */
    String districtCode;

    /**
     * Ward code
     */
    String wardCode;

    /**
     * Geographic latitude
     */
    Double latitude;

    /**
     * Geographic longitude
     */
    Double longitude;

    /**
     * User preferences (embedded document)
     */
    UserPreference userPreference;
}
