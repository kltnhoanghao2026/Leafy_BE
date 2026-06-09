package com.leafy.profileservice.model;

import com.leafy.common.model.BaseModel;
import com.leafy.profileservice.model.enums.ConsultationStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * UserConnection model
 * Tracks follow and consultation relationships between users (e.g., FARMER following EXPERT or another FARMER)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = false)
@Document("user_connections")
public class UserConnection extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    /**
     * Profile ID of the user who follows or requests consultation
     */
    @Indexed
    String followerId;

    /**
     * Profile ID of the target user being followed or consulted
     */
    @Indexed
    String followingId;

    /**
     * True if the follower is currently following the target
     */
    @Builder.Default
    Boolean isFollowing = false;

    /**
     * The status of the consultation relationship (if any)
     */
    @Builder.Default
    ConsultationStatus consultationStatus = ConsultationStatus.NONE;
}
