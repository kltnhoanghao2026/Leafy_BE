package com.leafy.searchservice.client.dto.community;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommunityPostStatsResponse {
    Integer upvoteCount;
    Integer downvoteCount;
    Integer commentCount;
    Integer shareCount;
}