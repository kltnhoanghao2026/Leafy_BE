package com.leafy.communityfeedservice.service.seeder;

import com.leafy.common.event.post.PostUpsertEvent;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.model.kafka.EventType;
import com.leafy.common.publisher.OutboxEventPublisher;
import com.leafy.common.security.UserPrincipal;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.communityfeedservice.client.ProfileServiceClient;
import com.leafy.communityfeedservice.client.SearchServiceClient;
import com.leafy.communityfeedservice.client.dto.ExternalApiResponse;
import com.leafy.communityfeedservice.client.dto.PagedResponse;
import com.leafy.communityfeedservice.client.dto.ProfileSummaryResponse;
import com.leafy.communityfeedservice.config.SeederProperties;
import com.leafy.communityfeedservice.dto.response.SeederResponse;
import com.leafy.communityfeedservice.model.Comment;
import com.leafy.communityfeedservice.model.Post;
import com.leafy.communityfeedservice.model.Vote;
import com.leafy.communityfeedservice.model.embedded.PostContent;
import com.leafy.communityfeedservice.model.embedded.PostMedia;
import com.leafy.communityfeedservice.model.embedded.PostStats;
import com.leafy.communityfeedservice.model.enums.PostType;
import com.leafy.communityfeedservice.model.enums.Visibility;
import com.leafy.communityfeedservice.model.enums.VoteTargetType;
import com.leafy.communityfeedservice.model.enums.VoteType;
import com.leafy.communityfeedservice.repository.CommentRepository;
import com.leafy.communityfeedservice.repository.PostRepository;
import com.leafy.communityfeedservice.repository.VoteRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SeederServiceImpl implements SeederService {

    static String ROLE_ADMIN = "ROLE_ADMIN";

    SeederProperties seederProperties;
    ProfileServiceClient profileServiceClient;
    PostRepository postRepository;
    CommentRepository commentRepository;
    VoteRepository voteRepository;
    Optional<OutboxEventPublisher> outboxEventPublisher;
    Optional<SearchServiceClient> searchServiceClient;

    @Override
    @Transactional
    public SeederResponse reseedCommunityFeed() {
        UserPrincipal currentUser = ServiceSecurityUtils.getCurrentUser();

        List<String> profileIds = fetchProfileIds(currentUser);
        if (profileIds.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        long deletedVoteCount = voteRepository.count();
        long deletedCommentCount = commentRepository.count();
        long deletedPostCount = postRepository.count();

        voteRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();

        resetSearchIndex();

        Random random = new Random(seederProperties.getRandomSeed());
        LocalDateTime now = LocalDateTime.now();

        List<Post> posts = seedPosts(profileIds, random, now);
        List<Comment> comments = seedComments(posts, profileIds, random);
        List<Vote> votes = seedVotes(posts, comments, profileIds, random);

        publishSeededPostEvents(posts);

        log.info(
                "Community feed reseeded: posts={}, comments={}, votes={}, profiles={}",
                posts.size(), comments.size(), votes.size(), profileIds.size());

        return SeederResponse.builder()
                .deletedPostCount(deletedPostCount)
                .deletedCommentCount(deletedCommentCount)
                .deletedVoteCount(deletedVoteCount)
                .seededPostCount(posts.size())
                .seededCommentCount(comments.size())
                .seededVoteCount(votes.size())
                .sourceProfileCount(profileIds.size())
                .build();
    }

    private List<String> fetchProfileIds(UserPrincipal currentUser) {
        Set<String> profileIds = new LinkedHashSet<>();

        for (int page = 0; page < seederProperties.getProfileMaxPages(); page++) {
            ExternalApiResponse<PagedResponse<ProfileSummaryResponse>> response = profileServiceClient.getActiveProfiles(
                    page,
                    seederProperties.getProfilePageSize(),
                    "createdAt",
                    "DESC");

            if (response == null || response.getData() == null || response.getData().getContent() == null) {
                break;
            }

            List<String> currentPageIds = response.getData().getContent().stream()
                    .map(ProfileSummaryResponse::getId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();

            profileIds.addAll(currentPageIds);

            if (currentPageIds.isEmpty() || page + 1 >= response.getData().getTotalPages()) {
                break;
            }
        }

        return new ArrayList<>(profileIds);
    }

    private List<Post> seedPosts(List<String> profileIds, Random random, LocalDateTime now) {
        List<Post> posts = new ArrayList<>(seederProperties.getPostCount());
        for (int i = 0; i < seederProperties.getPostCount(); i++) {
            String authorId = pickRandomProfileId(profileIds, random);
            Post post = Post.builder()
                    .authorId(authorId)
                    .postType(PostType.FEED)
                    .visibility(random.nextBoolean() ? Visibility.ALL : Visibility.FRIEND)
                    .content(PostContent.builder()
                            .title("Seed Post #" + (i + 1))
                            .caption("Community feed seeded content " + (i + 1))
                            .description("Auto-generated seeded post for development and QA checks")
                            .hashtags(List.of("#leafy", "#seeded", "#community"))
                            .build())
                    .media(List.of(PostMedia.builder()
                            .url("https://picsum.photos/seed/leafy-post-" + (i + 1) + "/1080/720")
                            .type("IMAGE")
                            .build()))
                    .stats(new PostStats())
                    .uploadedAt(now.minusMinutes(i * 3L))
                    .updatedAt(now.minusMinutes(i * 2L))
                    .build();
            post.setActive(true);
            posts.add(post);
        }

        return postRepository.saveAll(posts);
    }

    private List<Comment> seedComments(List<Post> posts, List<String> profileIds, Random random) {
        int totalCommentCount = seederProperties.getCommentCount();
        int rootCommentCount = (int) Math.round(totalCommentCount * 0.7);

        List<Comment> rootComments = new ArrayList<>(rootCommentCount);
        for (int i = 0; i < rootCommentCount; i++) {
            Post post = posts.get(random.nextInt(posts.size()));
            Comment comment = Comment.builder()
                    .postId(post.getId())
                    .authorId(pickRandomProfileId(profileIds, random))
                    .content("Root comment #" + (i + 1) + " for post " + post.getId())
                    .replyDepth(0)
                    .build();
            comment.setActive(true);
            rootComments.add(comment);
        }

        rootComments = commentRepository.saveAll(rootComments);

        Map<String, List<Comment>> rootCommentsByPostId = rootComments.stream()
                .collect(Collectors.groupingBy(Comment::getPostId));

        int replyCommentCount = Math.max(totalCommentCount - rootComments.size(), 0);
        List<Comment> replyComments = new ArrayList<>(replyCommentCount);
        for (int i = 0; i < replyCommentCount; i++) {
            Post post = posts.get(random.nextInt(posts.size()));
            List<Comment> rootsForPost = rootCommentsByPostId.get(post.getId());
            if (rootsForPost == null || rootsForPost.isEmpty()) {
                continue;
            }

            Comment parent = rootsForPost.get(random.nextInt(rootsForPost.size()));
            Comment reply = Comment.builder()
                    .postId(post.getId())
                    .authorId(pickRandomProfileId(profileIds, random))
                    .parentId(parent.getId())
                    .content("Reply #" + (i + 1) + " to comment " + parent.getId())
                    .replyDepth(1)
                    .build();
            reply.setActive(true);
            replyComments.add(reply);
        }

        replyComments = commentRepository.saveAll(replyComments);

        List<Comment> allComments = new ArrayList<>(rootComments.size() + replyComments.size());
        allComments.addAll(rootComments);
        allComments.addAll(replyComments);

        Map<String, Long> commentsPerPost = allComments.stream()
                .collect(Collectors.groupingBy(Comment::getPostId, Collectors.counting()));
        posts.forEach(post -> {
            PostStats stats = post.getStats() == null ? new PostStats() : post.getStats();
            stats.setCommentCount(commentsPerPost.getOrDefault(post.getId(), 0L).intValue());
            post.setStats(stats);
        });
        postRepository.saveAll(posts);

        Map<String, Integer> replyCountByParent = new HashMap<>();
        for (Comment reply : replyComments) {
            replyCountByParent.merge(reply.getParentId(), 1, Integer::sum);
        }
        rootComments.forEach(root -> root.setReplyCount(replyCountByParent.getOrDefault(root.getId(), 0)));
        commentRepository.saveAll(rootComments);

        return allComments;
    }

    private List<Vote> seedVotes(List<Post> posts, List<Comment> comments, List<String> profileIds, Random random) {
        List<Vote> votes = new ArrayList<>(seederProperties.getVoteCount());
        Set<String> uniqueVoteKeys = new HashSet<>(seederProperties.getVoteCount() * 2);

        int maxAttempts = seederProperties.getVoteCount() * 20;
        int attempts = 0;
        while (votes.size() < seederProperties.getVoteCount() && attempts < maxAttempts) {
            attempts++;
            boolean voteOnComment = !comments.isEmpty() && random.nextInt(100) < 40;

            VoteTargetType targetType = voteOnComment ? VoteTargetType.COMMENT : VoteTargetType.POST;
            String targetId = voteOnComment
                    ? comments.get(random.nextInt(comments.size())).getId()
                    : posts.get(random.nextInt(posts.size())).getId();

            String authorId = pickRandomProfileId(profileIds, random);
            String uniqueKey = authorId + "|" + targetType + "|" + targetId;
            if (!uniqueVoteKeys.add(uniqueKey)) {
                continue;
            }

            Vote vote = Vote.builder()
                    .authorId(authorId)
                    .targetId(targetId)
                    .targetType(targetType)
                    .type(random.nextBoolean() ? VoteType.UPVOTE : VoteType.DOWNVOTE)
                    .build();
            vote.setActive(true);
            votes.add(vote);
        }

        votes = voteRepository.saveAll(votes);

        Map<String, Integer> postUpvotes = new HashMap<>();
        Map<String, Integer> postDownvotes = new HashMap<>();
        Map<String, Integer> commentUpvotes = new HashMap<>();
        Map<String, Integer> commentDownvotes = new HashMap<>();

        for (Vote vote : votes) {
            if (vote.getTargetType() == VoteTargetType.POST) {
                if (vote.getType() == VoteType.UPVOTE) {
                    postUpvotes.merge(vote.getTargetId(), 1, Integer::sum);
                } else {
                    postDownvotes.merge(vote.getTargetId(), 1, Integer::sum);
                }
            } else {
                if (vote.getType() == VoteType.UPVOTE) {
                    commentUpvotes.merge(vote.getTargetId(), 1, Integer::sum);
                } else {
                    commentDownvotes.merge(vote.getTargetId(), 1, Integer::sum);
                }
            }
        }

        posts.forEach(post -> {
            PostStats stats = post.getStats() == null ? new PostStats() : post.getStats();
            stats.setUpvoteCount(postUpvotes.getOrDefault(post.getId(), 0));
            stats.setDownvoteCount(postDownvotes.getOrDefault(post.getId(), 0));
            post.setStats(stats);
        });
        postRepository.saveAll(posts);

        comments.forEach(comment -> {
            comment.setUpvoteCount(commentUpvotes.getOrDefault(comment.getId(), 0));
            comment.setDownvoteCount(commentDownvotes.getOrDefault(comment.getId(), 0));
        });
        commentRepository.saveAll(comments);

        return votes;
    }

    private void resetSearchIndex() {
        searchServiceClient.ifPresentOrElse(
                client -> {
                    client.resetPostIndex();
                    log.info("Post index in search-service reset before seeding");
                },
                () -> log.warn("SearchServiceClient unavailable. Skipping post index reset")
        );
    }

    private void publishSeededPostEvents(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }
        outboxEventPublisher.ifPresentOrElse(
                publisher -> {
                    for (Post post : posts) {
                        PostUpsertEvent event = PostUpsertEvent.builder()
                                .postId(post.getId())
                                .build();
                        publisher.saveAndPublish(post.getId(), "Post", EventType.POST_UPSERTED, event);
                    }
                    log.info("Published {} post upsert events to Kafka for search-service indexing", posts.size());
                },
                () -> log.warn("OutboxEventPublisher unavailable. Skipping Kafka events for {} seeded posts", posts.size())
        );
    }

    private String pickRandomProfileId(List<String> profileIds, Random random) {
        return profileIds.get(random.nextInt(profileIds.size()));
    }
}
