package com.leafy.profileservice.model;

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
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = false)
@Document("profile")
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
     * URL to user's certificate document
     */
    String certificate;

    /**
     * User's biography or description
     */
    String bio;

    /**
     * User preferences (embedded document)
     */
    UserPreference userPreference;
}
