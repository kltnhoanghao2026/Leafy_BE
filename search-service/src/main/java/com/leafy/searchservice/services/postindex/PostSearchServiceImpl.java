package com.leafy.searchservice.services.postindex;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.leafy.searchservice.config.ElasticSearchProperties;
import com.leafy.searchservice.dto.response.AuthorInfoResponse;
import com.leafy.searchservice.dto.response.PostSearchResponse;
import com.leafy.searchservice.model.elasticsearch.PostIndex;
import com.leafy.searchservice.model.elasticsearch.ProfileIndex;
import com.leafy.searchservice.repository.ProfileIndexSearchRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostSearchServiceImpl implements PostSearchService {

    ElasticsearchOperations elasOps;
    ElasticSearchProperties elasProps;
    ProfileIndexSearchRepository profileIndexSearchRepository;

    private void debugLog(String runId, String hypothesisId, String location, String message, Map<String, Object> data) {
        // #region agent log
        try {
            String sessionId = "ccdb9f";
            String id = "log_" + System.currentTimeMillis() + "_" + UUID.randomUUID();

            String safeData = data == null ? "{}" : data.entrySet().stream()
                    .map(e -> "\"" + String.valueOf(e.getKey()).replace("\\", "\\\\").replace("\"", "\\\"") + "\":" + toJsonValue(e.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));

            String payload = "{"
                    + "\"sessionId\":\"" + sessionId + "\""
                    + ",\"id\":\"" + id + "\""
                    + ",\"timestamp\":" + System.currentTimeMillis()
                    + ",\"location\":\"" + escapeJson(location) + "\""
                    + ",\"message\":\"" + escapeJson(message) + "\""
                    + ",\"runId\":\"" + escapeJson(runId) + "\""
                    + ",\"hypothesisId\":\"" + escapeJson(hypothesisId) + "\""
                    + ",\"data\":" + safeData
                    + "}";

            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://127.0.0.1:7297/ingest/cce84058-e304-43cb-b8b9-fcbb2a10791f"))
                    .timeout(java.time.Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .header("X-Debug-Session-Id", sessionId)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            java.net.http.HttpClient.newHttpClient().sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding());
        } catch (Throwable ignored) {
        }
        // #endregion
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String toJsonValue(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return String.valueOf(v);
        }
        return "\"" + escapeJson(String.valueOf(v)) + "\"";
    }

    @Override
    public Page<PostSearchResponse> searchPosts(
            String keyword,
            String postType,
            String authorId,
            Pageable pageable
    ) {
        // #region agent log
        debugLog(
                "pre-fix",
                "H1",
                "PostSearchServiceImpl.java:searchPosts:entry",
                "searchPosts called",
                Map.of(
                        "hasKeyword", keyword != null && !keyword.trim().isEmpty(),
                        "keywordLength", keyword != null ? keyword.length() : 0,
                        "postTypeProvided", postType != null && !postType.trim().isEmpty(),
                        "authorIdProvided", authorId != null && !authorId.trim().isEmpty(),
                        "page", pageable != null ? pageable.getPageNumber() : null,
                        "size", pageable != null ? pageable.getPageSize() : null,
                        "indexAlias", elasProps != null ? elasProps.getPostAlias() : null
                )
        );
        // #endregion

        if (!StringUtils.hasText(keyword)) {
            return Page.empty(pageable);
        }

        String searchQuery = keyword.trim();
        String normalizedQuery = searchQuery.toLowerCase();
        String normalizedPostType = StringUtils.hasText(postType) ? postType.trim().toUpperCase() : null;
        String normalizedAuthorId = StringUtils.hasText(authorId) ? authorId.trim() : null;
        String normalizedHashtag = normalizedQuery.startsWith("#")
            ? normalizedQuery
            : "#" + normalizedQuery;

        Query query = Query.of(q -> q.bool(b -> {
            // Vietnamese-aware main text match (edge-ngram + ICU analyzers)
            b.should(s -> s.multiMatch(mm -> mm
                    .fields(
                            "title^3",
                            "caption^2",
                            "authorName^2"
                    )
                    .query(searchQuery)
                    .boost(0.5f)
            ));

            // Fuzzy fallback via .fuzzy subfields
            b.should(s -> s.multiMatch(mm -> mm
                    .fields(
                            "title.fuzzy^3",
                            "caption.fuzzy^2",
                            "authorName.fuzzy^2"
                    )
                    .query(searchQuery)
                    .fuzziness("AUTO")
                    .prefixLength(1)
                    .maxExpansions(50)
                    .type(TextQueryType.BestFields)
                    .boost(1.5f)
            ));

            b.should(s -> s.term(t -> t.field("id").value(searchQuery)));
            b.should(s -> s.term(t -> t.field("authorName.keyword").value(normalizedQuery)));
            b.should(s -> s.term(t -> t.field("title.keyword").value(normalizedQuery)));
            b.should(s -> s.term(t -> t.field("hashtags").value(searchQuery)));

            if (!normalizedHashtag.equals(searchQuery)) {
            b.should(s -> s.term(t -> t.field("hashtags").value(normalizedHashtag)));
            }

            b.minimumShouldMatch("1");

            if (StringUtils.hasText(normalizedPostType)) {
                b.filter(f -> f.term(t -> t.field("postType").value(normalizedPostType)));
            }

            if (StringUtils.hasText(normalizedAuthorId)) {
                b.filter(f -> f.term(t -> t.field("authorId").value(normalizedAuthorId)));
            }

            return b;
        }));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable)
                .build();

        SearchHits<PostIndex> searchHits;
        try {
            searchHits = elasOps.search(
                    nativeQuery,
                    PostIndex.class,
                    IndexCoordinates.of(elasProps.getPostAlias())
            );
            // #region agent log
            debugLog(
                    "pre-fix",
                    "H2",
                    "PostSearchServiceImpl.java:searchPosts:afterSearch",
                    "Elasticsearch search succeeded",
                    Map.of("totalHits", searchHits.getTotalHits())
            );
            // #endregion
        } catch (Exception e) {
            // #region agent log
            debugLog(
                    "pre-fix",
                    "H3",
                    "PostSearchServiceImpl.java:searchPosts:searchException",
                    "Elasticsearch search failed",
                    Map.of(
                            "exceptionClass", e.getClass().getName(),
                            "exceptionMessage", String.valueOf(e.getMessage()),
                            "indexAlias", elasProps != null ? elasProps.getPostAlias() : null
                    )
            );
            // #endregion
            throw e;
        }

        List<PostIndex> posts = searchHits.stream()
                .map(hit -> hit.getContent())
                .toList();

        Map<String, AuthorInfoResponse> authorInfoMap = fetchAuthorInfoMap(posts);

        List<PostSearchResponse> results = posts.stream()
                .map(post -> toPostSearchResponse(post, authorInfoMap))
                .toList();

        return new PageImpl<>(results, pageable, searchHits.getTotalHits());
    }

    private Map<String, AuthorInfoResponse> fetchAuthorInfoMap(List<PostIndex> posts) {
        Set<String> authorIds = posts.stream()
                .map(PostIndex::getAuthorId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (authorIds.isEmpty()) {
            return Map.of();
        }

        return StreamSupport.stream(profileIndexSearchRepository.findAllById(authorIds).spliterator(), false)
                .collect(Collectors.toMap(
                        ProfileIndex::getId,
                        profile -> AuthorInfoResponse.builder()
                                .id(profile.getId())
                                .fullName(profile.getFullName())
                                .avatar(profile.getAvatar() != null ? profile.getAvatar() : profile.getProfilePicture())
                                .role(profile.getRole() != null ? profile.getRole().name() : null)
                                .isVerified(profile.getIsVerified())
                                .build()
                ));
    }

    private PostSearchResponse toPostSearchResponse(PostIndex postIndex, Map<String, AuthorInfoResponse> authorInfoMap) {
        AuthorInfoResponse authorInfo = authorInfoMap.get(postIndex.getAuthorId());
        return PostSearchResponse.builder()
                .id(postIndex.getId())
                .authorId(postIndex.getAuthorId())
                .authorInfo(authorInfo)
                .title(postIndex.getTitle())
                .caption(postIndex.getCaption())
                .hashtags(postIndex.getHashtags())
                .postType(postIndex.getPostType())
                .upvoteCount(postIndex.getUpvoteCount())
                .commentCount(postIndex.getCommentCount())
                .uploadedAt(postIndex.getUploadedAt())
                .build();
    }
}