package com.leafy.communityfeedservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.communityfeedservice.dto.response.PostResponse;
import com.leafy.communityfeedservice.service.post.PostService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/posts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalPostController {

    PostService postService;

    @GetMapping("/{postId}")
    public ApiResponse<PostResponse> getPostById(@PathVariable String postId) {
        return ApiResponse.success(postService.getPostById(postId));
    }

    @GetMapping("/batch")
    public ApiResponse<List<PostResponse>> getPostsBatch(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "200") int size) {
        List<PostResponse> posts = postService.getAllFeedAndSharedPosts(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        return ApiResponse.success(posts);
    }
}