package com.leafy.communityfeedservice.service.comment;

import com.leafy.common.event.community.CommentEvent;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.model.kafka.EventType;
import com.leafy.common.publisher.OutboxEventPublisher;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.communityfeedservice.dto.request.CommentCreateRequest;
import com.leafy.communityfeedservice.dto.request.CommentUpdateRequest;
import com.leafy.communityfeedservice.dto.response.CommentResponse;
import com.leafy.communityfeedservice.mapper.CommentMapper;
import com.leafy.communityfeedservice.model.Comment;
import com.leafy.communityfeedservice.model.Post;
import com.leafy.communityfeedservice.model.ProfileSummary;
import com.leafy.communityfeedservice.repository.CommentRepository;
import com.leafy.communityfeedservice.repository.PostRepository;
import com.leafy.communityfeedservice.repository.ProfileSummaryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommentServiceImpl implements CommentService {

    CommentRepository commentRepository;
    PostRepository postRepository;
    CommentMapper commentMapper;
    OutboxEventPublisher outboxEventPublisher;
    ProfileSummaryRepository profileSummaryRepository;

    @Override
    @Transactional
    public CommentResponse createComment(CommentCreateRequest request) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        Comment comment = commentMapper.toEntity(request);
        comment.setAuthorId(currentProfileId);
        comment.setActive(true);

        if (request.getParentId() != null && !request.getParentId().isBlank()) {
            Comment parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND)); 
            if (!parent.getPostId().equals(post.getId())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
            comment.setReplyDepth(parent.getReplyDepth() + 1);
        } else {
            comment.setReplyDepth(0);
        }

        comment = commentRepository.save(comment);

        CommentEvent eventPayload = CommentEvent.builder()
                .commentId(comment.getId())
                .postId(comment.getPostId())
                .parentId(comment.getParentId())
                .authorId(currentProfileId)
                .build();
        
        outboxEventPublisher.saveAndPublish(comment.getId(), "COMMENT", EventType.COMMENT_CREATED, eventPayload);

        return enrichCommentResponse(commentMapper.toResponse(comment));
    }

    @Override
    public CommentResponse getCommentById(String id) {
        Comment comment = commentRepository.findById(id)
                .filter(Comment::isActive)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
        return enrichCommentResponse(commentMapper.toResponse(comment));
    }

    @Override
    public Page<CommentResponse> getCommentsByPostId(String postId, Pageable pageable) {
        Page<Comment> commentPage = commentRepository.findByPostIdAndParentIdIsNullAndActiveTrue(postId, pageable);
        List<CommentResponse> responses = commentPage.getContent().stream()
                .map(commentMapper::toResponse)
                .collect(Collectors.toList());
        enrichCommentResponses(responses);
        return new PageImpl<>(responses, pageable, commentPage.getTotalElements());
    }

    @Override
    public Page<CommentResponse> getRepliesByCommentId(String parentId, Pageable pageable) {
        Page<Comment> commentPage = commentRepository.findByParentIdAndActiveTrue(parentId, pageable);
        List<CommentResponse> responses = commentPage.getContent().stream()
                .map(commentMapper::toResponse)
                .collect(Collectors.toList());
        enrichCommentResponses(responses);
        return new PageImpl<>(responses, pageable, commentPage.getTotalElements());
    }

    @Override
    @Transactional
    public CommentResponse updateComment(String id, CommentUpdateRequest request) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        Comment comment = commentRepository.findById(id)
                .filter(Comment::isActive)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
                
        if (!comment.getAuthorId().equals(currentProfileId)) {
            throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        commentMapper.updateEntityFromRequest(request, comment);
        comment.setEdited(true);
        comment = commentRepository.save(comment);

        return enrichCommentResponse(commentMapper.toResponse(comment));
    }

    @Override
    @Transactional
    public void deleteComment(String id) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        Comment comment = commentRepository.findById(id)
                .filter(Comment::isActive)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
                
        if (!comment.getAuthorId().equals(currentProfileId)) {
            throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        comment.setActive(false);
        commentRepository.save(comment);

        CommentEvent eventPayload = CommentEvent.builder()
                .commentId(comment.getId())
                .postId(comment.getPostId())
                .parentId(comment.getParentId())
                .authorId(currentProfileId)
                .build();
        
        outboxEventPublisher.saveAndPublish(comment.getId(), "COMMENT", EventType.COMMENT_DELETED, eventPayload);
    }

    private CommentResponse enrichCommentResponse(CommentResponse response) {
        profileSummaryRepository.findById(response.getAuthorId())
                .ifPresent(response::setAuthorInfo);
        return response;
    }

    private void enrichCommentResponses(List<CommentResponse> responses) {
        Set<String> authorIds = responses.stream()
                .map(CommentResponse::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (authorIds.isEmpty()) return;

        Map<String, ProfileSummary> profileMap = profileSummaryRepository
                .findAllByIdIn(new ArrayList<>(authorIds)).stream()
                .collect(Collectors.toMap(ProfileSummary::getId, Function.identity()));

        responses.forEach(r -> r.setAuthorInfo(profileMap.get(r.getAuthorId())));
    }
}
