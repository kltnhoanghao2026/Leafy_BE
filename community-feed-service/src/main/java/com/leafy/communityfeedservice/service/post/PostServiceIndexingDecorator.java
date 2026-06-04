package com.leafy.communityfeedservice.service.post;

import com.leafy.common.event.post.PostDeletedEvent;
import com.leafy.common.event.post.PostUpsertEvent;
import com.leafy.common.model.kafka.EventType;
import com.leafy.common.publisher.OutboxEventPublisher;
import com.leafy.communityfeedservice.dto.request.PostCreateRequest;
import com.leafy.communityfeedservice.dto.request.PostUpdateRequest;
import com.leafy.communityfeedservice.dto.response.PostResponse;
import com.leafy.communityfeedservice.model.enums.PostType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Primary
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostServiceIndexingDecorator implements PostService {

    PostServiceImpl delegate;
    Optional<OutboxEventPublisher> outboxEventPublisher;

    @Override
    public PostResponse createPost(PostCreateRequest request) {
        PostResponse response = delegate.createPost(request);
        if (isSearchIndexable(response.getPostType())) {
            publishUpsert(response.getId());
        }
        return response;
    }

    @Override
    public PostResponse getPostById(String id) {
        return delegate.getPostById(id);
    }

    @Override
    public Page<PostResponse> getAllFeedAndSharedPosts(Pageable pageable) {
        return delegate.getAllFeedAndSharedPosts(pageable);
    }

    @Override
    public Page<PostResponse> getPostsByUserId(String profileId, Pageable pageable) {
        return delegate.getPostsByUserId(profileId, pageable);
    }

    @Override
    public Page<PostResponse> getPersonalizedFeed(String profileId, Pageable pageable) {
        return delegate.getPersonalizedFeed(profileId, pageable);
    }


    @Override
    public PostResponse updatePost(String id, PostUpdateRequest request) {
        PostResponse response = delegate.updatePost(id, request);
        if (isSearchIndexable(response.getPostType())) {
            publishUpsert(response.getId());
        }
        return response;
    }

    @Override
    public void deletePost(String id) {
        delegate.deletePost(id);
        publishDelete(id);
    }

    @Override
    public void markPostAsViewed(String profileId, String postId) {
        delegate.markPostAsViewed(profileId, postId);
    }

    private boolean isSearchIndexable(PostType postType) {
        return postType == PostType.FEED || postType == PostType.SHARE;
    }

    private void publishUpsert(String postId) {
        if (postId == null) {
            return;
        }

        PostUpsertEvent event = PostUpsertEvent.builder()
                .postId(postId)
                .build();

        outboxEventPublisher.ifPresentOrElse(
                publisher -> publisher.saveAndPublish(postId, "Post", EventType.POST_UPSERTED, event),
                () -> log.warn("OutboxEventPublisher is unavailable. Skip post upsert event for postId={}", postId)
        );
    }

    private void publishDelete(String postId) {
        if (postId == null) {
            return;
        }

        PostDeletedEvent event = PostDeletedEvent.builder()
                .postId(postId)
                .build();

        outboxEventPublisher.ifPresentOrElse(
                publisher -> publisher.saveAndPublish(postId, "Post", EventType.POST_DELETED, event),
                () -> log.warn("OutboxEventPublisher is unavailable. Skip post delete event for postId={}", postId)
        );
    }
}