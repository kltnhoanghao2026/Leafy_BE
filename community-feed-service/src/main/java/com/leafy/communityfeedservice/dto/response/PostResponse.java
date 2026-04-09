package com.leafy.communityfeedservice.dto.response;

import com.leafy.communityfeedservice.model.ProfileSummary;
import com.leafy.communityfeedservice.model.embedded.*;
import com.leafy.communityfeedservice.model.enums.PostType;
import com.leafy.communityfeedservice.model.enums.Visibility;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostResponse {
    String id;
    String authorId;
    ProfileSummary authorInfo;
    String groupId;
    PostContent content;
    List<PostMedia> media;
    PostType postType;
    String sharedPostId;
    String originalAuthorId;
    PostContent sharedCaption;
    PostResponse sharedPostInfo;
    String rootPostId;
    LocationInfo location;
    Visibility visibility;
    PostStats stats;
    LocalDateTime uploadedAt;
    LocalDateTime updatedAt;
    boolean isEdited;
}
