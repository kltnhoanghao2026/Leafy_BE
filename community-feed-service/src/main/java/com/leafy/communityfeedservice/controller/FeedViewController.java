package com.leafy.communityfeedservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.communityfeedservice.service.post.PostService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for community feed view tracking.
 * Exposed to frontend clients via API Gateway.
 */
@RestController
@RequestMapping("/feed")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class FeedViewController {

    PostService postService;

    /**
     * Mark multiple posts as viewed by the current user.
     * Called by frontend when posts become visible via IntersectionObserver.
     *
     * @param postIds list of post IDs to mark as viewed
     * @return success response
     */
    @PostMapping("/viewed")
    public ResponseEntity<ApiResponse<Void>> markPostsViewed(
            @RequestBody List<String> postIds) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        if (currentProfileId != null) {
            postService.markPostsAsViewed(currentProfileId, postIds);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Unmark (remove) posts from the viewed list for the current user.
     *
     * @param postIds list of post IDs to unmark
     * @return success response
     */
    @DeleteMapping("/viewed")
    public ResponseEntity<ApiResponse<Void>> unmarkPostsViewed(
            @RequestBody List<String> postIds) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        if (currentProfileId != null) {
            postService.unmarkPostsAsViewed(currentProfileId, postIds);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
