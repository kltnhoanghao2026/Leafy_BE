package com.leafy.communityfeedservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.communityfeedservice.dto.request.CommentCreateRequest;
import com.leafy.communityfeedservice.dto.request.CommentUpdateRequest;
import com.leafy.communityfeedservice.dto.response.CommentResponse;
import com.leafy.communityfeedservice.service.comment.CommentService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommentController {

    CommentService commentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentResponse> createComment(@Valid @RequestBody CommentCreateRequest request) {
        return ApiResponse.success(commentService.createComment(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<CommentResponse> getCommentById(@PathVariable String id) {
        return ApiResponse.success(commentService.getCommentById(id));
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<Page<CommentResponse>> getCommentsByPostId(
            @PathVariable String postId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(commentService.getCommentsByPostId(postId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/{parentId}/replies")
    public ApiResponse<Page<CommentResponse>> getRepliesByCommentId(
            @PathVariable String parentId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(commentService.getRepliesByCommentId(parentId, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"))));
    }

    @PutMapping("/{id}")
    public ApiResponse<CommentResponse> updateComment(
            @PathVariable String id,
            @Valid @RequestBody CommentUpdateRequest request) {
        return ApiResponse.success(commentService.updateComment(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable String id) {
        commentService.deleteComment(id);
        return ApiResponse.success(null);
    }
}
