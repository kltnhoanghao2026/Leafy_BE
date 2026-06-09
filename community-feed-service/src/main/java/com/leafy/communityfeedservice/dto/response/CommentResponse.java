package com.leafy.communityfeedservice.dto.response;

import com.leafy.communityfeedservice.model.ProfileSummary;
import com.leafy.communityfeedservice.model.embedded.PostMedia;
import com.leafy.communityfeedservice.model.enums.VoteType;
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
public class CommentResponse {
    String id;
    String postId;
    String authorId;
    ProfileSummary authorInfo;
    String parentId;
    String content;
    List<PostMedia> media;
    int replyDepth;
    int replyCount;
    int upvoteCount;
    int downvoteCount;
    VoteType currentUserVoteType; // null when the caller has not voted
    boolean isEdited;
    boolean active;
    LocalDateTime createdAt;
    LocalDateTime lastModifiedAt;
}
