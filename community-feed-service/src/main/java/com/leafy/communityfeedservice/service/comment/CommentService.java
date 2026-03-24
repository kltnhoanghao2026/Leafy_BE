package com.leafy.communityfeedservice.service.comment;

import com.leafy.communityfeedservice.dto.request.CommentCreateRequest;
import com.leafy.communityfeedservice.dto.request.CommentUpdateRequest;
import com.leafy.communityfeedservice.dto.response.CommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentService {
    CommentResponse createComment(CommentCreateRequest request);
    CommentResponse getCommentById(String id);
    Page<CommentResponse> getCommentsByPostId(String postId, Pageable pageable);
    Page<CommentResponse> getRepliesByCommentId(String parentId, Pageable pageable);
    CommentResponse updateComment(String id, CommentUpdateRequest request);
    void deleteComment(String id);
}
