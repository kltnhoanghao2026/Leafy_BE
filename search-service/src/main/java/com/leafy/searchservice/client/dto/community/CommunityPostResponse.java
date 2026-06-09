package com.leafy.searchservice.client.dto.community;

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
public class CommunityPostResponse {
    String id;
    String authorId;
    CommunityProfileSummaryResponse authorInfo;
    String groupId;
    CommunityPostContentResponse content;
    List<CommunityPostMediaResponse> media;
    String postType;
    String sharedPostId;
    String originalAuthorId;
    CommunityPostContentResponse sharedCaption;
    CommunityPostResponse sharedPostInfo;
    String rootPostId;
    CommunityLocationInfoResponse location;
    String visibility;
    CommunityPostStatsResponse stats;
    LocalDateTime uploadedAt;
    LocalDateTime updatedAt;
    boolean edited;
}