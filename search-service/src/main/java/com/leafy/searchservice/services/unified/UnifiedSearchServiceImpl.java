package com.leafy.searchservice.services.unified;

import com.leafy.searchservice.dto.response.PlanSearchResponse;
import com.leafy.searchservice.dto.response.PostSearchResponse;
import com.leafy.searchservice.dto.response.ProfileResponse;
import com.leafy.searchservice.dto.response.UnifiedSearchResponse;
import com.leafy.searchservice.services.planindex.PlanSearchService;
import com.leafy.searchservice.services.postindex.PostSearchService;
import com.leafy.searchservice.services.profileindex.ProfileSearchService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UnifiedSearchServiceImpl implements UnifiedSearchService {

    PostSearchService postSearchService;
    ProfileSearchService profileSearchService;
    PlanSearchService planSearchService;

    @Override
    public UnifiedSearchResponse search(String searchTerm, int postSize, int profileSize, int planSize) {
        if (!StringUtils.hasText(searchTerm) || searchTerm.trim().length() < 2) {
            return UnifiedSearchResponse.builder()
                    .searchTerm(searchTerm)
                    .posts(List.of())
                    .profiles(List.of())
                    .plans(List.of())
                    .totalPosts(0)
                    .totalProfiles(0)
                    .totalPlans(0)
                    .postSize(postSize)
                    .profileSize(profileSize)
                    .planSize(planSize)
                    .build();
        }

        String term = searchTerm.trim();

        // Run all three searches in parallel
        CompletableFuture<Page<PostSearchResponse>> postsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return postSearchService.searchPosts(term, null, null, PageRequest.of(0, postSize));
            } catch (Exception ex) {
                log.warn("Post search failed in unified search for term '{}': {}", term, ex.getMessage());
                return Page.empty();
            }
        });

        CompletableFuture<Page<ProfileResponse>> profilesFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return profileSearchService.searchProfile(term, null, null, null, PageRequest.of(0, profileSize));
            } catch (Exception ex) {
                log.warn("Profile search failed in unified search for term '{}': {}", term, ex.getMessage());
                return Page.empty();
            }
        });

        CompletableFuture<Page<PlanSearchResponse>> plansFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return planSearchService.searchPlans(term, null, null, null, PageRequest.of(0, planSize));
            } catch (Exception ex) {
                log.warn("Plan search failed in unified search for term '{}': {}", term, ex.getMessage());
                return Page.empty();
            }
        });

        // Wait for all and combine
        CompletableFuture.allOf(postsFuture, profilesFuture, plansFuture).join();

        Page<PostSearchResponse> postsPage = postsFuture.join();
        Page<ProfileResponse> profilesPage = profilesFuture.join();
        Page<PlanSearchResponse> plansPage = plansFuture.join();

        return UnifiedSearchResponse.builder()
                .searchTerm(term)
                .posts(postsPage.getContent())
                .profiles(profilesPage.getContent())
                .plans(plansPage.getContent())
                .totalPosts(postsPage.getTotalElements())
                .totalProfiles(profilesPage.getTotalElements())
                .totalPlans(plansPage.getTotalElements())
                .postSize(postSize)
                .profileSize(profileSize)
                .planSize(planSize)
                .build();
    }
}
