package com.leafy.communityfeedservice.service.post;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.communityfeedservice.dto.request.PostCreateRequest;
import com.leafy.communityfeedservice.dto.request.PostUpdateRequest;
import com.leafy.communityfeedservice.dto.response.PostResponse;
import com.leafy.communityfeedservice.mapper.PostMapper;
import com.leafy.communityfeedservice.model.Post;
import com.leafy.communityfeedservice.model.embedded.PostStats;
import com.leafy.communityfeedservice.model.enums.PostType;
import com.leafy.communityfeedservice.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostServiceImpl implements PostService {

    PostRepository postRepository;
    PostMapper postMapper;

    @Override
    @Transactional
    public PostResponse createPost(PostCreateRequest request) {
        validatePostTypeConstraints(request);

        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();

        Post post = postMapper.toEntity(request);
        post.setAuthorId(currentProfileId);
        post.setStats(new PostStats());

        post = postRepository.save(post);
        return postMapper.toResponse(post);
    }

    private void validatePostTypeConstraints(PostCreateRequest request) {
        if (request.postType() == PostType.SHARE) {
            if (request.sharedPostId() == null || request.sharedPostId().isBlank()) {
                throw new AppException(ErrorCode.INVALID_POST_TYPE_CONSTRAINT);
            }
            if (request.originalAuthorId() == null || request.originalAuthorId().isBlank()) {
                throw new AppException(ErrorCode.INVALID_POST_TYPE_CONSTRAINT);
            }
        }
    }

    @Override
    public PostResponse getPostById(String id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED)); 
        return postMapper.toResponse(post);
    }

    @Override
    @Transactional
    public PostResponse updatePost(String id, PostUpdateRequest request) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED));
        
        if (!post.getAuthorId().equals(currentProfileId)) {
            throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        postMapper.updateEntityFromRequest(request, post);
        post.setEdited(true);
        post = postRepository.save(post);
        return postMapper.toResponse(post);
    }

    @Override
    @Transactional
    public void deletePost(String id) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED));
        
        if (!post.getAuthorId().equals(currentProfileId)) {
            throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        
        postRepository.delete(post);
    }
}
