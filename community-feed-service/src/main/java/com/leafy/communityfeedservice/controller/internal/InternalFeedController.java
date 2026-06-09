package com.leafy.communityfeedservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.communityfeedservice.service.post.PostService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal REST Controller for feed operations.
 * Handles service-to-service operations for tracking viewed posts.
 */
@RestController
@RequestMapping("/internal/feed")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class InternalFeedController {

    PostService postService;

    /**
     * Mark a single post as viewed by a user (internal service-to-service).
     *
     * @param postId the ID of the post to mark as viewed
     * @param userId the profile ID of the user
     * @return success response
     */
    @PostMapping("/posts/{postId}/viewed")
    public ResponseEntity<ApiResponse<Void>> markPostViewed(
            @PathVariable String postId,
            @RequestParam String profileId) {
        postService.markPostAsViewed(profileId, postId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
