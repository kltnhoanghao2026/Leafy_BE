package com.leafy.communityfeedservice.model;

import com.leafy.common.model.BaseModel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

/**
 * Tracks which posts a user has viewed.
 * Used to filter out viewed posts from the personalized feed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document("viewed_posts")
public class ViewedPost extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    /**
     * Profile ID of the user who viewed the post
     */
    @Indexed
    String userId;

    /**
     * The ID of the post that was viewed
     */
    @Indexed
    String postId;

    /**
     * Timestamp when the post was viewed
     */
    @Indexed
    LocalDateTime viewedAt;
}
