package com.leafy.searchservice.client;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.client.dto.community.CommunityPostResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "community-feed-service", path = "/internal/posts")
public interface CommunityPostClient {

    @GetMapping("/{postId}")
    ApiResponse<CommunityPostResponse> getPostById(@PathVariable("postId") String postId);

    @GetMapping("/batch")
    ApiResponse<List<CommunityPostResponse>> getPostsBatch(
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}