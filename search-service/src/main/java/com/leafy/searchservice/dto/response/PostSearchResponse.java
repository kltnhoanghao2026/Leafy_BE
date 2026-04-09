package com.leafy.searchservice.dto.response;

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
public class PostSearchResponse {
    String id;
    String authorId;
    AuthorInfoResponse authorInfo;
    String title;
    String caption;
    List<String> hashtags;
    String postType;
    Integer upvoteCount;
    Integer commentCount;
    LocalDateTime uploadedAt;
    Boolean current;
}