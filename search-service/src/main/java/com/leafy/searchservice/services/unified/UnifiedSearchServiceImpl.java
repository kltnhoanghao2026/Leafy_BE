package com.leafy.searchservice.services.unified;

import com.leafy.common.enums.ProfileRole;
import com.leafy.searchservice.dto.response.PostSearchResponse;
import com.leafy.searchservice.dto.response.ProfileResponse;
import com.leafy.searchservice.dto.response.UnifiedSearchResponse;
import com.leafy.searchservice.services.postindex.PostSearchService;
import com.leafy.searchservice.services.profileindex.ProfileSearchService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
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

    @Override
    public UnifiedSearchResponse search(String searchTerm, int postSize, int profileSize) {
        if (!StringUtils.hasText(searchTerm) || searchTerm.trim().length() < 2) {
            return UnifiedSearchResponse.builder()
                    .searchTerm(searchTerm)
                    .posts(List.of())
                    .profiles(List.of())
                    .totalPosts(0)
                    .totalProfiles(0)
                    .postSize(postSize)
                    .profileSize(profileSize)
                    .build();
        }

        String term = searchTerm.trim();

        // Run both searches in parallel
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

        // Wait for both and combine
        CompletableFuture.allOf(postsFuture, profilesFuture).join();

        Page<PostSearchResponse> postsPage = postsFuture.join();
        Page<ProfileResponse> profilesPage = profilesFuture.join();

        return UnifiedSearchResponse.builder()
                .searchTerm(term)
                .posts(postsPage.getContent())
                .profiles(profilesPage.getContent())
                .totalPosts(postsPage.getTotalElements())
                .totalProfiles(profilesPage.getTotalElements())
                .postSize(postSize)
                .profileSize(profileSize)
                .build();
    }
}
