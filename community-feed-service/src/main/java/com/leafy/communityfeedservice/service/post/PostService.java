package com.leafy.communityfeedservice.service.post;

import com.leafy.communityfeedservice.dto.request.PostCreateRequest;
import com.leafy.communityfeedservice.dto.request.PostUpdateRequest;
import com.leafy.communityfeedservice.dto.response.PostResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PostService {
    PostResponse createPost(PostCreateRequest request);
    PostResponse getPostById(String id);
    Page<PostResponse> getAllFeedAndSharedPosts(Pageable pageable);
    Page<PostResponse> getPostsByUserId(String userId, Pageable pageable);
    Page<PostResponse> getPersonalizedFeed(String userId, Pageable pageable);
    PostResponse updatePost(String id, PostUpdateRequest request);
    void deletePost(String id);
    void markPostsAsViewed(String userId, List<String> postIds);
    void unmarkPostsAsViewed(String userId, List<String> postIds);
}
