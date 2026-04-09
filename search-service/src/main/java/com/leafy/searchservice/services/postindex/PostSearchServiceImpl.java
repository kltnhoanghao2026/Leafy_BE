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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostSearchServiceImpl implements PostSearchService {

    ElasticsearchOperations elasOps;
    ElasticSearchProperties elasProps;
    ProfileIndexSearchRepository profileIndexSearchRepository;

    @Override
    public Page<PostSearchResponse> searchPosts(
            String keyword,
            String postType,
            String authorId,
            Pageable pageable
    ) {
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
            b.should(s -> s.multiMatch(mm -> mm
                    .fields(
                            "title^3",
                            "caption^2",
                    "authorName^2"
                    )
                    .query(searchQuery)
                    .fuzziness("1")
                    .maxExpansions(50)
                    .type(TextQueryType.BestFields)
            ));

            b.should(s -> s.term(t -> t.field("id").value(searchQuery)));
            b.should(s -> s.term(t -> t.field("authorName.keyword").value(searchQuery)));
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

        SearchHits<PostIndex> searchHits = elasOps.search(
                nativeQuery,
                PostIndex.class,
                IndexCoordinates.of(elasProps.getPostAlias())
        );

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