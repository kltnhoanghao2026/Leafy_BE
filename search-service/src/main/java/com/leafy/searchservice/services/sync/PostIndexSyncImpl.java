package com.leafy.searchservice.services.sync;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.client.CommunityPostClient;
import com.leafy.searchservice.client.dto.community.CommunityPostContentResponse;
import com.leafy.searchservice.client.dto.community.CommunityPostResponse;
import com.leafy.searchservice.client.dto.community.CommunityPostStatsResponse;
import com.leafy.searchservice.client.dto.community.CommunityProfileSummaryResponse;
import com.leafy.searchservice.config.ElasticSearchProperties;
import com.leafy.searchservice.model.elasticsearch.PostIndex;
import com.leafy.searchservice.repository.PostIndexSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostIndexSyncImpl {

    private final CommunityPostClient communityPostClient;
    private final PostIndexSearchRepository postIndexSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticSearchProperties elasticSearchProperties;

    public int reindexAll(int pageSize) {
        String postIndexAlias = elasticSearchProperties.getPostAlias();
        IndexOperations indexOperations = elasticsearchOperations.indexOps(IndexCoordinates.of(postIndexAlias));

        if (indexOperations.exists()) {
            indexOperations.delete();
            log.info("Deleted existing Elasticsearch post index for alias={}", postIndexAlias);
        }

        indexOperations.create();
        indexOperations.putMapping(indexOperations.createMapping(PostIndex.class));
        log.info("Created fresh Elasticsearch post index and mapping for alias={}", postIndexAlias);

        int indexedCount = 0;
        int page = 0;

        while (true) {
            List<CommunityPostResponse> posts = getPostsBatch(page, pageSize);
            if (posts.isEmpty()) {
                break;
            }

            List<PostIndex> documents = posts.stream()
                    .map(this::toPostIndex)
                    .filter(document -> document != null)
                    .toList();

            if (!documents.isEmpty()) {
                postIndexSearchRepository.saveAll(documents);
                indexedCount += documents.size();
            }

            page += 1;
        }

        return indexedCount;
    }

    public void upsertPost(String postId) {
        CommunityPostResponse post = getPostById(postId);
        PostIndex document = toPostIndex(post);

        if (document == null) {
            postIndexSearchRepository.deleteById(postId);
            log.info("Removed post from index because it no longer qualifies for search: postId={}", postId);
            return;
        }

        postIndexSearchRepository.save(document);
        log.info("Upserted post index document: postId={}", postId);
    }

    public void deletePost(String postId) {
        postIndexSearchRepository.deleteById(postId);
        log.info("Deleted post index document: postId={}", postId);
    }

    private CommunityPostResponse getPostById(String postId) {
        ApiResponse<CommunityPostResponse> response = communityPostClient.getPostById(postId);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("Community feed service returned empty post data for postId=" + postId);
        }
        return response.data();
    }

    private List<CommunityPostResponse> getPostsBatch(int page, int pageSize) {
        ApiResponse<List<CommunityPostResponse>> response = communityPostClient.getPostsBatch(page, pageSize);
        if (response == null || response.data() == null) {
            return Collections.emptyList();
        }
        return response.data();
    }

    private PostIndex toPostIndex(CommunityPostResponse post) {
        if (!shouldIndex(post)) {
            return null;
        }

        CommunityProfileSummaryResponse authorInfo = post.getAuthorInfo();
        CommunityPostStatsResponse stats = post.getStats();

        return PostIndex.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .authorName(authorInfo != null ? authorInfo.getFullName() : null)
                .authorAvatar(authorInfo != null ? authorInfo.getAvatar() : null)
                .authorRole(authorInfo != null ? authorInfo.getRole() : null)
                .authorVerified(authorInfo != null ? authorInfo.getIsVerified() : null)
            .title(resolveIndexedTitle(post))
            .caption(resolveIndexedCaption(post))
            .hashtags(resolveIndexedHashtags(post))
                .postType(post.getPostType())
                .upvoteCount(stats != null ? stats.getUpvoteCount() : null)
                .commentCount(stats != null ? stats.getCommentCount() : null)
            .uploadedAt(truncateToSecond(post.getUploadedAt() != null ? post.getUploadedAt() : post.getUpdatedAt()))
                .current(post.isCurrent())
                .build();
    }

    private boolean shouldIndex(CommunityPostResponse post) {
        if (post == null || !post.isCurrent()) {
            return false;
        }

        return "FEED".equalsIgnoreCase(post.getPostType())
                || "SHARE".equalsIgnoreCase(post.getPostType());
    }

    private String resolveIndexedTitle(CommunityPostResponse post) {
        CommunityPostContentResponse content = post.getContent();
        if (content != null && content.getTitle() != null && !content.getTitle().isBlank()) {
            return content.getTitle().trim();
        }

        CommunityPostResponse sharedPostInfo = post.getSharedPostInfo();
        if (sharedPostInfo != null && sharedPostInfo.getContent() != null) {
            String sharedTitle = sharedPostInfo.getContent().getTitle();
            if (sharedTitle != null && !sharedTitle.isBlank()) {
                return sharedTitle.trim();
            }
        }

        return null;
    }

    private String resolveIndexedCaption(CommunityPostResponse post) {
        List<String> parts = new ArrayList<>();
        addContentText(parts, post.getContent());

        if (post.getSharedPostInfo() != null) {
            addContentText(parts, post.getSharedPostInfo().getContent());
        } else {
            addContentText(parts, post.getSharedCaption());
        }

        if (parts.isEmpty()) {
            return null;
        }

        return String.join("\n", parts);
    }

    private List<String> resolveIndexedHashtags(CommunityPostResponse post) {
        List<String> hashtags = new ArrayList<>();
        addHashtags(hashtags, post.getContent());

        if (post.getSharedPostInfo() != null) {
            addHashtags(hashtags, post.getSharedPostInfo().getContent());
        } else {
            addHashtags(hashtags, post.getSharedCaption());
        }

        if (hashtags.isEmpty()) {
            return null;
        }

        return hashtags.stream().distinct().toList();
    }

    private void addContentText(List<String> parts, CommunityPostContentResponse content) {
        if (content == null) {
            return;
        }

        if (content.getCaption() != null && !content.getCaption().isBlank()) {
            parts.add(content.getCaption().trim());
        }

        if (content.getDescription() != null && !content.getDescription().isBlank()) {
            parts.add(content.getDescription().trim());
        }
    }

    private void addHashtags(List<String> target, CommunityPostContentResponse content) {
        if (content == null || content.getHashtags() == null) {
            return;
        }

        target.addAll(content.getHashtags().stream()
                .filter(hashtag -> hashtag != null && !hashtag.isBlank())
                .map(String::trim)
                .toList());
    }

    private LocalDateTime truncateToSecond(LocalDateTime value) {
        if (value == null) {
            return null;
        }

        return value.truncatedTo(ChronoUnit.SECONDS);
    }
}