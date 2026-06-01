package com.leafy.communityfeedservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.communityfeedservice.service.post.PostService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * Mark a single post as viewed by the current user.
     *
     * @param postId the ID of the post to mark as viewed
     * @return success response
     */
    @PostMapping("/posts/{postId}/viewed")
    public ResponseEntity<ApiResponse<Void>> markPostViewed(
            @PathVariable String postId) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        if (currentProfileId != null) {
            postService.markPostAsViewed(currentProfileId, postId);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
