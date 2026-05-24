package com.leafy.communityfeedservice.service.post;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.communityfeedservice.client.PlantManagementServiceClient;
import com.leafy.communityfeedservice.client.ProfileServiceClient;
import com.leafy.communityfeedservice.client.dto.ExternalApiResponse;
import com.leafy.communityfeedservice.client.dto.PlanSummaryResponse;
import com.leafy.communityfeedservice.dto.request.PostCreateRequest;
import com.leafy.communityfeedservice.dto.request.PostUpdateRequest;
import com.leafy.communityfeedservice.dto.response.PlanInfo;
import com.leafy.communityfeedservice.dto.response.PostResponse;
import com.leafy.communityfeedservice.mapper.PostMapper;
import com.leafy.communityfeedservice.model.Post;
import com.leafy.communityfeedservice.model.ProfileSummary;
import com.leafy.communityfeedservice.model.ViewedPost;
import com.leafy.communityfeedservice.model.Vote;
import com.leafy.communityfeedservice.model.embedded.PostStats;
import com.leafy.communityfeedservice.model.enums.PostType;
import com.leafy.communityfeedservice.model.enums.VoteTargetType;
import com.leafy.communityfeedservice.model.enums.Visibility;
import com.leafy.communityfeedservice.repository.PostRepository;
import com.leafy.communityfeedservice.repository.ProfileSummaryRepository;
import com.leafy.communityfeedservice.repository.ViewedPostRepository;
import com.leafy.communityfeedservice.repository.VoteRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostServiceImpl implements PostService {

    private static final List<PostType> FEED_AND_SHARED_TYPES = List.of(PostType.FEED, PostType.SHARE, PostType.PLAN_SHARE);
    private static final int RANDOM_POST_COUNT = 5;
    // Visibility levels for personalized feed
    private static final List<Visibility> PERSONALIZED_VISIBILITIES = List.of(Visibility.ALL, Visibility.FOLLOWER);
    private static final List<Visibility> RANDOM_VISIBILITIES = List.of(Visibility.ALL);

    PostRepository postRepository;
    PostMapper postMapper;
    ProfileSummaryRepository profileSummaryRepository;
    VoteRepository voteRepository;
    ViewedPostRepository viewedPostRepository;
    ProfileServiceClient profileServiceClient;
    Optional<PlantManagementServiceClient> plantManagementServiceClient;


    @Override
    @Transactional
    public PostResponse createPost(PostCreateRequest request) {
        validatePostTypeConstraints(request);

        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();

        Post post = postMapper.toEntity(request);
        post.setAuthorId(currentProfileId);
        post.setStats(new PostStats());

        post = postRepository.save(post);
        return enrichPostResponse(postMapper.toResponse(post));
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
        if (request.postType() == PostType.PLAN_SHARE) {
            if (request.planId() == null || request.planId().isBlank()) {
                throw new AppException(ErrorCode.INVALID_POST_TYPE_CONSTRAINT);
            }
        }
    }

    @Override
    public PostResponse getPostById(String id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED)); 
        return enrichPostResponse(postMapper.toResponse(post));
    }

    @Override
    public Page<PostResponse> getAllFeedAndSharedPosts(Pageable pageable) {
        Page<Post> postPage = postRepository.findByPostTypeIn(FEED_AND_SHARED_TYPES, pageable);
        List<PostResponse> responses = postPage.getContent().stream()
                .map(postMapper::toResponse)
                .collect(Collectors.toList());
        enrichPostResponses(responses);
        return new PageImpl<>(responses, pageable, postPage.getTotalElements());
    }

    @Override
    public Page<PostResponse> getPostsByUserId(String userId, Pageable pageable) {
        Page<Post> postPage = postRepository.findByAuthorId(userId, pageable);
        List<PostResponse> responses = postPage.getContent().stream()
                .map(postMapper::toResponse)
                .collect(Collectors.toList());
        enrichPostResponses(responses);
        return new PageImpl<>(responses, pageable, postPage.getTotalElements());
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
        return enrichPostResponse(postMapper.toResponse(post));
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

    private PostResponse enrichPostResponse(PostResponse response) {
        profileSummaryRepository.findById(response.getAuthorId())
                .ifPresent(response::setAuthorInfo);

        if (response.getSharedPostId() != null) {
            postRepository.findById(response.getSharedPostId()).ifPresent(sharedPost -> {
                PostResponse sharedResponse = postMapper.toResponse(sharedPost);
                profileSummaryRepository.findById(sharedPost.getAuthorId())
                        .ifPresent(sharedResponse::setAuthorInfo);
                response.setSharedPostInfo(sharedResponse);
            });
        }

        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        if (currentProfileId != null) {
            voteRepository.findByAuthorIdAndTargetIdAndTargetType(
                    currentProfileId, response.getId(), VoteTargetType.POST)
                    .filter(Vote::isActive)
                    .ifPresent(vote -> response.setCurrentUserVoteType(vote.getType()));
        }

        // Embed plan snapshot for PLAN_SHARE posts
        if (PostType.PLAN_SHARE.equals(response.getPostType()) && response.getPlanId() != null) {
            PlanInfo planInfo = fetchPlanInfo(response.getPlanId());
            response.setPlanInfo(planInfo);
        }

        return response;
    }

    /** Fetch a single plan from plant-management-service and map to PlanInfo. Returns null on failure. */
    private PlanInfo fetchPlanInfo(String planId) {
        if (plantManagementServiceClient.isEmpty() || planId == null) return null;
        try {
            ExternalApiResponse<PlanSummaryResponse> resp =
                    plantManagementServiceClient.get().getPlanById(planId);
            if (resp == null || resp.getData() == null) return null;
            return toPlanInfo(resp.getData());
        } catch (Exception ex) {
            log.warn("Could not fetch planInfo for planId={}: {}", planId, ex.getMessage());
            return null;
        }
    }

    private PlanInfo toPlanInfo(PlanSummaryResponse src) {
        if (src == null) return null;
        return PlanInfo.builder()
                .id(src.getId())
                .planName(src.getPlanName())
                .diseaseName(src.getDiseaseName())
                .severityLevel(src.getSeverityLevel())
                .urgency(src.getUrgency())
                .estimatedCost(src.getEstimatedCost())
                .confidenceScore(src.getConfidenceScore())
                .requiredInputs(src.getRequiredInputs())
                .safetyWarnings(src.getSafetyWarnings())
                .successIndicators(src.getSuccessIndicators())
                .applyCount(src.getApplyCount())
                .eventCount(src.getPlantEventIds() != null ? src.getPlantEventIds().size() : 0)
                .isPublic(src.isPublic())
                .createdAt(src.getCreatedAt())
                .build();
    }

    private void enrichPostResponses(List<PostResponse> responses) {
        // Collect all author IDs and shared post IDs
        Set<String> profileIds = new HashSet<>();
        Set<String> sharedPostIds = new HashSet<>();

        for (PostResponse r : responses) {
            if (r.getAuthorId() != null) profileIds.add(r.getAuthorId());
            if (r.getOriginalAuthorId() != null) profileIds.add(r.getOriginalAuthorId());
            if (r.getSharedPostId() != null) sharedPostIds.add(r.getSharedPostId());
        }

        // Batch fetch profiles
        Map<String, ProfileSummary> profileMap = profileSummaryRepository
                .findAllByIdIn(new ArrayList<>(profileIds)).stream()
                .collect(Collectors.toMap(ProfileSummary::getId, Function.identity()));

        // Batch fetch shared posts
        Map<String, Post> sharedPostMap = sharedPostIds.isEmpty()
                ? Collections.emptyMap()
                : postRepository.findAllById(sharedPostIds).stream()
                        .collect(Collectors.toMap(Post::getId, Function.identity()));

        // Collect shared post author IDs for a second profile batch
        Set<String> sharedAuthorIds = sharedPostMap.values().stream()
                .map(Post::getAuthorId)
                .filter(id -> id != null && !profileMap.containsKey(id))
                .collect(Collectors.toSet());

        if (!sharedAuthorIds.isEmpty()) {
            profileSummaryRepository.findAllByIdIn(new ArrayList<>(sharedAuthorIds))
                    .forEach(p -> profileMap.put(p.getId(), p));
        }

        // Batch fetch current user's votes for all posts
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        Map<String, Vote> voteMap = Collections.emptyMap();
        if (currentProfileId != null) {
            List<String> postIds = responses.stream()
                    .map(PostResponse::getId)
                    .collect(Collectors.toList());
            voteMap = voteRepository.findByAuthorIdAndTargetIdInAndTargetTypeAndActiveTrue(
                    currentProfileId, postIds, VoteTargetType.POST).stream()
                    .collect(Collectors.toMap(Vote::getTargetId, Function.identity()));
        }

        // Enrich each response
        for (PostResponse r : responses) {
            r.setAuthorInfo(profileMap.get(r.getAuthorId()));

            if (r.getSharedPostId() != null) {
                Post sharedPost = sharedPostMap.get(r.getSharedPostId());
                if (sharedPost != null) {
                    PostResponse sharedResponse = postMapper.toResponse(sharedPost);
                    sharedResponse.setAuthorInfo(profileMap.get(sharedPost.getAuthorId()));
                    r.setSharedPostInfo(sharedResponse);
                }
            }

            Vote vote = voteMap.get(r.getId());
            if (vote != null) {
                r.setCurrentUserVoteType(vote.getType());
            }

            // Embed plan snapshot for PLAN_SHARE posts
            if (PostType.PLAN_SHARE.equals(r.getPostType()) && r.getPlanId() != null) {
                r.setPlanInfo(fetchPlanInfo(r.getPlanId()));
            }
        }
    }

    @Override
    public Page<PostResponse> getPersonalizedFeed(String userId, Pageable pageable) {
        if (userId == null || userId.isBlank()) {
            return getRandomUnviewedPosts(pageable);
        }

        // Get viewed post IDs to exclude
        List<String> viewedPostIds = viewedPostRepository.findPostIdsByUserId(userId);

        // Get followed users + consulting relationships
        Set<String> relevantAuthorIds = getRelevantAuthorIds(userId);

        // Get followers for FOLLOWER visibility check
        Set<String> followerIds = getFollowerIds(userId);

        // Query personalized posts with visibility filtering
        Page<Post> personalizedPage;
        if (relevantAuthorIds.isEmpty()) {
            // No connections - fall back to random posts only
            return getRandomUnviewedPosts(viewedPostIds, pageable);
        }

        if (viewedPostIds.isEmpty()) {
            personalizedPage = postRepository.findByAuthorIdInAndPostTypeInAndVisibilityIn(
                    relevantAuthorIds, FEED_AND_SHARED_TYPES, PERSONALIZED_VISIBILITIES, pageable);
        } else {
            personalizedPage = postRepository.findByAuthorIdInAndPostTypeInAndVisibilityInAndIdNotIn(
                    relevantAuthorIds, FEED_AND_SHARED_TYPES, PERSONALIZED_VISIBILITIES, viewedPostIds, pageable);
        }

        List<PostResponse> personalizedPosts = filterByVisibility(
                personalizedPage.getContent(), userId, relevantAuthorIds, followerIds).stream()
                .map(postMapper::toResponse)
                .collect(Collectors.toList());
        enrichPostResponses(personalizedPosts);

        // If personalized posts are fewer than requested, fill with random unviewed posts
        if (personalizedPosts.size() < pageable.getPageSize()) {
            int needed = pageable.getPageSize() - personalizedPosts.size();
            List<Post> randomPosts = getRandomPosts(viewedPostIds, needed + RANDOM_POST_COUNT);

            // Filter out posts already in personalized list
            Set<String> existingIds = personalizedPosts.stream()
                    .map(PostResponse::getId)
                    .collect(Collectors.toSet());

            List<PostResponse> fillPosts = randomPosts.stream()
                    .filter(p -> !existingIds.contains(p.getId()))
                    .limit(needed)
                    .map(postMapper::toResponse)
                    .collect(Collectors.toList());
            enrichPostResponses(fillPosts);

            personalizedPosts.addAll(fillPosts);

            // Return adjusted page with correct total
            long total = personalizedPage.getTotalElements() +
                    Math.max(0, getTotalRandomUnviewedCount(viewedPostIds) - personalizedPage.getTotalElements());
            return new PageImpl<>(personalizedPosts, pageable, total);
        }

        return new PageImpl<>(personalizedPosts, pageable, personalizedPage.getTotalElements());
    }

    /**
     * Filter posts by visibility rules:
     * - ALL: visible to everyone
     * - FOLLOWER: visible only to followers of the author
     * - ONLY_ME: visible only to the author
     */
    private List<Post> filterByVisibility(
            List<Post> posts,
            String currentUserId,
            Set<String> relevantAuthorIds,
            Set<String> followerIds) {
        return posts.stream()
                .filter(post -> {
                    Visibility visibility = post.getVisibility();
                    if (visibility == null) {
                        return true; // Default to visible
                    }
                    String authorId = post.getAuthorId();

                    switch (visibility) {
                        case ALL:
                            return true;
                        case FOLLOWER:
                            // Check if current user follows the author
                            return followerIds.contains(currentUserId) || authorId.equals(currentUserId);
                        case ONLY_ME:
                            // Only the author can see this post
                            return authorId.equals(currentUserId);
                        default:
                            return true;
                    }
                })
                .collect(Collectors.toList());
    }

    private Set<String> getRelevantAuthorIds(String userId) {
        Set<String> authorIds = new HashSet<>();

        // 1. Get users the current user follows
        try {
            ExternalApiResponse<List<String>> followingResponse =
                    profileServiceClient.getFollowingUserIds(userId);
            if (followingResponse != null && followingResponse.getData() != null) {
                authorIds.addAll(followingResponse.getData());
            }
        } catch (Exception e) {
            log.warn("Failed to get following users for userId={}: {}", userId, e.getMessage());
        }

        // 2. Get farmers being consulted (if user is an expert)
        try {
            ExternalApiResponse<List<String>> consultingResponse =
                    profileServiceClient.getConsultingFarmers(userId);
            if (consultingResponse != null && consultingResponse.getData() != null) {
                authorIds.addAll(consultingResponse.getData());
            }
        } catch (Exception e) {
            log.warn("Failed to get consulting farmers for userId={}: {}", userId, e.getMessage());
        }

        // 3. Also include the user themselves (to see own posts)
        authorIds.add(userId);

        return authorIds;
    }

    private Set<String> getFollowerIds(String userId) {
        Set<String> followerIds = new HashSet<>();
        try {
            ExternalApiResponse<List<String>> followersResponse =
                    profileServiceClient.getFollowerUserIds(userId);
            if (followersResponse != null && followersResponse.getData() != null) {
                followerIds.addAll(followersResponse.getData());
            }
        } catch (Exception e) {
            log.warn("Failed to get follower IDs for userId={}: {}", userId, e.getMessage());
        }
        return followerIds;
    }

    private Page<PostResponse> getRandomUnviewedPosts(List<String> viewedPostIds, Pageable pageable) {
        List<Post> randomPosts = getRandomPosts(viewedPostIds, pageable.getPageSize());
        List<PostResponse> responses = randomPosts.stream()
                .map(postMapper::toResponse)
                .collect(Collectors.toList());
        enrichPostResponses(responses);
        return new PageImpl<>(responses, pageable, randomPosts.size());
    }

    private Page<PostResponse> getRandomUnviewedPosts(Pageable pageable) {
        return getRandomUnviewedPosts(Collections.emptyList(), pageable);
    }

    private List<Post> getRandomPosts(List<String> excludeIds, int limit) {
        // Random posts are only visible if visibility is ALL
        if (excludeIds.isEmpty()) {
            return postRepository.findRandomByPostTypeInAndVisibility(
                    FEED_AND_SHARED_TYPES, Visibility.ALL, limit);
        } else {
            return postRepository.findRandomByPostTypeInAndVisibilityInAndIdNotIn(
                    FEED_AND_SHARED_TYPES, RANDOM_VISIBILITIES, excludeIds, limit);
        }
    }

    private long getTotalRandomUnviewedCount(List<String> viewedPostIds) {
        return postRepository.countByPostTypeIn(FEED_AND_SHARED_TYPES);
    }

    @Override
    @Transactional
    public void markPostsAsViewed(String userId, List<String> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<ViewedPost> viewedPosts = postIds.stream()
                .filter(postId -> !viewedPostRepository.existsByUserIdAndPostId(userId, postId))
                .map(postId -> ViewedPost.builder()
                        .userId(userId)
                        .postId(postId)
                        .viewedAt(now)
                        .build())
                .toList();
        if (!viewedPosts.isEmpty()) {
            viewedPostRepository.saveAll(viewedPosts);
        }
    }

    @Override
    @Transactional
    public void unmarkPostsAsViewed(String userId, List<String> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return;
        }
        viewedPostRepository.deleteAllByUserIdAndPostIdIn(userId, postIds);
    }
}

