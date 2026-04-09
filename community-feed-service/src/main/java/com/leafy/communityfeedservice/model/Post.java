package com.leafy.communityfeedservice.model;

import com.leafy.common.model.BaseModel;

import com.leafy.communityfeedservice.model.embedded.*;
import com.leafy.communityfeedservice.model.enums.PostType;
import com.leafy.communityfeedservice.model.enums.Visibility;
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
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document("posts")
public class Post extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    @Indexed
    String authorId;

    @Indexed
    String groupId;

    PostContent content;

    List<PostMedia> media;

    @Indexed
    PostType postType;

    @Indexed
    String sharedPostId;

    @Indexed
    String originalAuthorId;

    PostContent sharedCaption;

    @Indexed
    String rootPostId;

    LocationInfo location;

    Visibility visibility;

    PostStats stats;

    LocalDateTime uploadedAt;

    LocalDateTime updatedAt;

    @Builder.Default
    boolean isEdited = false;
}
