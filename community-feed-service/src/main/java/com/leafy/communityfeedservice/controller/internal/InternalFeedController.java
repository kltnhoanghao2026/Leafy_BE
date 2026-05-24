package com.leafy.communityfeedservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.communityfeedservice.service.post.PostService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * Mark multiple posts as viewed by a user.
     *
     * @param userId  the profile ID of the user
     * @param postIds list of post IDs to mark as viewed
     * @return success response
     */
    @PostMapping("/posts/viewed")
    public ResponseEntity<ApiResponse<Void>> markPostsViewed(
            @RequestParam String userId,
            @RequestBody List<String> postIds) {
        postService.markPostsAsViewed(userId, postIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Unmark (remove) posts from the viewed list for a user.
     *
     * @param userId  the profile ID of the user
     * @param postIds list of post IDs to unmark
     * @return success response
     */
    @DeleteMapping("/posts/viewed")
    public ResponseEntity<ApiResponse<Void>> unmarkPostsViewed(
            @RequestParam String userId,
            @RequestBody List<String> postIds) {
        postService.unmarkPostsAsViewed(userId, postIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
