package com.leafy.communityfeedservice.service.post;

import com.leafy.communityfeedservice.dto.request.PostCreateRequest;
import com.leafy.communityfeedservice.dto.request.PostUpdateRequest;
import com.leafy.communityfeedservice.dto.response.PostResponse;

public interface PostService {
    PostResponse createPost(PostCreateRequest request);
    PostResponse getPostById(String id);
    PostResponse updatePost(String id, PostUpdateRequest request);
    void deletePost(String id);
}
