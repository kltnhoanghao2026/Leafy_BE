package com.leafy.communityfeedservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.communityfeedservice.dto.response.SeederResponse;
import com.leafy.communityfeedservice.service.seeder.SeederService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/seed")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SeederController {

    SeederService seederService;

    /**
     * Wipes and reseeds posts, comments and votes for the community feed.
     * All counts default to values configured in {@code community-feed.seeder.*}.
     *
     * @param postCount    number of posts to seed (falls back to community-feed.seeder.post-count)
     * @param commentCount number of comments to seed (falls back to community-feed.seeder.comment-count)
     * @param voteCount    number of votes to seed (falls back to community-feed.seeder.vote-count)
     */
    @PostMapping("/community")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SeederResponse> reseedCommunityFeed(
            @RequestParam(required = false) Integer postCount,
            @RequestParam(required = false) Integer commentCount,
            @RequestParam(required = false) Integer voteCount) {
        return ApiResponse.success(seederService.reseedCommunityFeed(postCount, commentCount, voteCount));
    }

    /**
     * Synchronizes profile summaries from profile-service.
     * Fetches all active profiles and upserts them into the local ProfileSummary repository.
     */
    @PostMapping("/profiles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SeederResponse> syncProfileSummaries() {
        return ApiResponse.success(seederService.syncProfileSummaries());
    }
}
