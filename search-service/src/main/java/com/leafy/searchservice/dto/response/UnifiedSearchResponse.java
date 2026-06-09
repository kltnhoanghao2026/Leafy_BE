package com.leafy.searchservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Unified search response that bundles post and profile results
 * from a single search term in one API call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UnifiedSearchResponse {
    String searchTerm;
    List<PostSearchResponse> posts;
    List<ProfileResponse> profiles;
    List<PlanSearchResponse> plans;
    long totalPosts;
    long totalProfiles;
    long totalPlans;
    int postSize;
    int profileSize;
    int planSize;
}
