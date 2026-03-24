package com.leafy.communityfeedservice.consumer;

import com.leafy.common.event.community.CommentEvent;
import com.leafy.common.event.community.VoteEvent;
import com.leafy.communityfeedservice.repository.CommentRepository;
import com.leafy.communityfeedservice.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommunityEventConsumer {

    PostRepository postRepository;
    CommentRepository commentRepository;

    @KafkaListener(topics = "${kafka.topics.community-events.comment-created:community.comment.created}", groupId = "${spring.application.name}")
    public void handleCommentCreated(CommentEvent event) {
        log.info("Received CommentCreatedEvent: {}", event);
        
        postRepository.findById(event.getPostId()).ifPresent(post -> {
            if (post.getStats() != null) {
                post.getStats().setCommentCount(post.getStats().getCommentCount() + 1);
            } else {
                post.setStats(com.leafy.communityfeedservice.model.embedded.PostStats.builder().commentCount(1).build());
            }
            postRepository.save(post);
        });

        if (event.getParentId() != null) {
            commentRepository.findById(event.getParentId()).ifPresent(parent -> {
                parent.setReplyCount(parent.getReplyCount() + 1);
                commentRepository.save(parent);
            });
        }
    }

    @KafkaListener(topics = "${kafka.topics.community-events.comment-deleted:community.comment.deleted}", groupId = "${spring.application.name}")
    public void handleCommentDeleted(CommentEvent event) {
        log.info("Received CommentDeletedEvent: {}", event);

        postRepository.findById(event.getPostId()).ifPresent(post -> {
            if (post.getStats() != null && post.getStats().getCommentCount() > 0) {
                post.getStats().setCommentCount(post.getStats().getCommentCount() - 1);
                postRepository.save(post);
            }
        });

        if (event.getParentId() != null) {
            commentRepository.findById(event.getParentId()).ifPresent(parent -> {
                if (parent.getReplyCount() > 0) {
                    parent.setReplyCount(parent.getReplyCount() - 1);
                    commentRepository.save(parent);
                }
            });
        }
    }

    @KafkaListener(topics = "${kafka.topics.community-events.vote-created:community.vote.created}", groupId = "${spring.application.name}")
    public void handleVoteCreated(VoteEvent event) {
        log.info("Received VoteCreatedEvent: {}", event);
        updateVoteCount(event.getTargetId(), event.getTargetType(), event.getVoteType(), 1);
    }

    @KafkaListener(topics = "${kafka.topics.community-events.vote-deleted:community.vote.deleted}", groupId = "${spring.application.name}")
    public void handleVoteDeleted(VoteEvent event) {
        log.info("Received VoteDeletedEvent: {}", event);
        updateVoteCount(event.getTargetId(), event.getTargetType(), event.getVoteType(), -1);
    }

    private void updateVoteCount(String targetId, String targetType, String voteType, int increment) {
        if ("POST".equalsIgnoreCase(targetType)) {
            postRepository.findById(targetId).ifPresent(post -> {
                if (post.getStats() == null) {
                    post.setStats(new com.leafy.communityfeedservice.model.embedded.PostStats());
                }
                if ("UPVOTE".equalsIgnoreCase(voteType)) {
                    post.getStats().setUpvoteCount(Math.max(0, post.getStats().getUpvoteCount() + increment));
                } else if ("DOWNVOTE".equalsIgnoreCase(voteType)) {
                    post.getStats().setDownvoteCount(Math.max(0, post.getStats().getDownvoteCount() + increment));
                }
                postRepository.save(post);
            });
        } else if ("COMMENT".equalsIgnoreCase(targetType)) {
            commentRepository.findById(targetId).ifPresent(comment -> {
                if ("UPVOTE".equalsIgnoreCase(voteType)) {
                    comment.setUpvoteCount(Math.max(0, comment.getUpvoteCount() + increment));
                } else if ("DOWNVOTE".equalsIgnoreCase(voteType)) {
                    comment.setDownvoteCount(Math.max(0, comment.getDownvoteCount() + increment));
                }
                commentRepository.save(comment);
            });
        }
    }
}
