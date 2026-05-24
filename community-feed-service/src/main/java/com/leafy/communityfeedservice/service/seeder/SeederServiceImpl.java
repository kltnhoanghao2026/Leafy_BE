package com.leafy.communityfeedservice.service.seeder;

import com.leafy.common.event.post.PostUpsertEvent;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.model.kafka.EventType;
import com.leafy.common.publisher.OutboxEventPublisher;
import com.leafy.common.security.UserPrincipal;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.communityfeedservice.client.PlantManagementServiceClient;
import com.leafy.communityfeedservice.client.ProfileServiceClient;
import com.leafy.communityfeedservice.client.SearchServiceClient;
import com.leafy.communityfeedservice.client.dto.ExternalApiResponse;
import com.leafy.communityfeedservice.client.dto.PagedResponse;
import com.leafy.communityfeedservice.client.dto.PlanSummaryResponse;
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
import com.leafy.communityfeedservice.repository.ProfileSummaryRepository;
import com.leafy.communityfeedservice.model.ProfileSummary;
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
    Optional<PlantManagementServiceClient> plantManagementServiceClient;
    PostRepository postRepository;
    CommentRepository commentRepository;
    VoteRepository voteRepository;
    ProfileSummaryRepository profileSummaryRepository;
    Optional<OutboxEventPublisher> outboxEventPublisher;
    Optional<SearchServiceClient> searchServiceClient;

    @Override
    @Transactional
    public SeederResponse reseedCommunityFeed(Integer postCount, Integer commentCount, Integer voteCount) {
        int resolvedPostCount    = postCount    != null ? postCount    : seederProperties.getPostCount();
        int resolvedCommentCount = commentCount != null ? commentCount : seederProperties.getCommentCount();
        int resolvedVoteCount    = voteCount    != null ? voteCount    : seederProperties.getVoteCount();

        UserPrincipal currentUser = ServiceSecurityUtils.getCurrentUser();

        List<String> profileIds = fetchProfileIds(currentUser);
        if (profileIds.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        List<String> planIds = fetchPublicPlanIds();
        log.info("Fetched {} public plan IDs from plant-management-service for seeding", planIds.size());

        long deletedVoteCount = voteRepository.count();
        long deletedCommentCount = commentRepository.count();
        long deletedPostCount = postRepository.count();

        voteRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();

        resetSearchIndex();

        Random random = new Random(seederProperties.getRandomSeed());
        LocalDateTime now = LocalDateTime.now();

        List<Post> posts = seedPosts(profileIds, planIds, random, now, resolvedPostCount);
        List<Comment> comments = seedComments(posts, profileIds, random, resolvedCommentCount);
        List<Vote> votes = seedVotes(posts, comments, profileIds, random, resolvedVoteCount);

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

    @Override
    @Transactional
    public SeederResponse syncProfileSummaries() {
        log.info("Starting Profile Summary synchronization from profile-service");
        List<ProfileSummary> summariesToSave = new ArrayList<>();
        int page = 0;
        boolean hasMore = true;

        while (hasMore && page < seederProperties.getProfileMaxPages()) {
            ExternalApiResponse<PagedResponse<ProfileSummaryResponse>> response = profileServiceClient.getActiveProfiles(
                    page,
                    seederProperties.getProfilePageSize(),
                    "createdAt",
                    "DESC");

            if (response == null || response.getData() == null || response.getData().getContent() == null || response.getData().getContent().isEmpty()) {
                break;
            }

            List<ProfileSummary> currentPageSummaries = response.getData().getContent().stream()
                    .filter(res -> res.getId() != null && !res.getId().isBlank())
                    .map(res -> ProfileSummary.builder()
                            .id(res.getId())
                            .fullName(res.getFullName())
                            .avatar(res.getAvatar())
                            .role(res.getRole())
                            .isVerified(res.getIsVerified())
                            .lastSyncedAt(LocalDateTime.now())
                            .build())
                    .toList();

            summariesToSave.addAll(currentPageSummaries);

            hasMore = page + 1 < response.getData().getTotalPages();
            page++;
        }

        if (!summariesToSave.isEmpty()) {
            profileSummaryRepository.saveAll(summariesToSave);
            log.info("Successfully synced {} profile summaries", summariesToSave.size());
        }

        return SeederResponse.builder()
                .seededProfileCount(summariesToSave.size())
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

    // ── Fetch real plan IDs from plant-management-service ─────────────────────

    private List<String> fetchPublicPlanIds() {
        if (plantManagementServiceClient.isEmpty()) {
            log.warn("PlantManagementServiceClient unavailable — no PLAN_SHARE posts will be seeded");
            return List.of();
        }

        List<String> planIds = new ArrayList<>();
        PlantManagementServiceClient client = plantManagementServiceClient.get();

        for (int page = 0; page < seederProperties.getPlanMaxPages(); page++) {
            ExternalApiResponse<PagedResponse<PlanSummaryResponse>> response;
            try {
                // Use the dedicated public endpoint — no admin privilege required,
                // and it only returns plans where isPublic=true.
                response = client.getPublicPlans(
                        page,
                        seederProperties.getPlanPageSize(),
                        "createdAt",
                        "DESC");
            } catch (Exception ex) {
                log.warn("Failed to fetch public plans from plant-management-service (page {}): {}", page, ex.getMessage());
                break;
            }

            if (response == null || response.getData() == null
                    || response.getData().getContent() == null
                    || response.getData().getContent().isEmpty()) {
                break;
            }

            response.getData().getContent().stream()
                    .filter(p -> p.getId() != null && !p.getId().isBlank())
                    .map(PlanSummaryResponse::getId)
                    .forEach(planIds::add);

            if (page + 1 >= response.getData().getTotalPages()) {
                break;
            }
        }

        log.info("Fetched {} public plan IDs via /plans/public for community seeding", planIds.size());
        return planIds;
    }


    private List<Post> seedPosts(List<String> profileIds, List<String> planIds, Random random, LocalDateTime now, int postCount) {
        List<Post> posts = new ArrayList<>(postCount);
        int planShareCount = planIds.isEmpty() ? 0 : Math.max(1, postCount / 5);
        int feedCount = postCount - planShareCount;

        for (int i = 0; i < feedCount; i++) {
            String authorId = pickRandomProfileId(profileIds, random);
            Post post = Post.builder()
                    .authorId(authorId)
                    .postType(PostType.FEED)
                    .visibility(random.nextBoolean() ? Visibility.ALL : Visibility.FOLLOWER)
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

        // Shuffle plan IDs so each PLAN_SHARE post gets a different real plan
        List<String> shuffledPlanIds = new ArrayList<>(planIds);
        java.util.Collections.shuffle(shuffledPlanIds, random);

        for (int i = 0; i < planShareCount; i++) {
            String authorId = pickRandomProfileId(profileIds, random);
            // Cycle through available plan IDs
            String planId = shuffledPlanIds.get(i % shuffledPlanIds.size());
            Post post = Post.builder()
                    .authorId(authorId)
                    .postType(PostType.PLAN_SHARE)
                    .planId(planId)
                    .visibility(Visibility.ALL)
                    .content(PostContent.builder()
                            .title("Chia sẻ kế hoạch điều trị #" + (i + 1))
                            .caption("Kế hoạch điều trị hiệu quả tôi đang áp dụng, chia sẻ để cùng tham khảo!")
                            .description("Auto-generated PLAN_SHARE post for development and QA checks")
                            .hashtags(List.of("#leafy", "#kehoach", "#benh"))
                            .build())
                    .media(List.of())
                    .stats(new PostStats())
                    .uploadedAt(now.minusMinutes((feedCount + i) * 3L))
                    .updatedAt(now.minusMinutes((feedCount + i) * 2L))
                    .build();
            post.setActive(true);
            posts.add(post);
        }

        return postRepository.saveAll(posts);
    }

    private List<Comment> seedComments(List<Post> posts, List<String> profileIds, Random random, int totalCommentCount) {
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

    private List<Vote> seedVotes(List<Post> posts, List<Comment> comments, List<String> profileIds, Random random, int voteCount) {
        List<Vote> votes = new ArrayList<>(voteCount);
        Set<String> uniqueVoteKeys = new HashSet<>(voteCount * 2);

        int maxAttempts = voteCount * 20;
        int attempts = 0;
        while (votes.size() < voteCount && attempts < maxAttempts) {
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
