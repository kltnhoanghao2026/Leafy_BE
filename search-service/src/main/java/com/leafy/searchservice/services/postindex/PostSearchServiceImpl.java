package com.leafy.searchservice.services.postindex;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.leafy.searchservice.config.ElasticSearchProperties;
import com.leafy.searchservice.dto.response.PostSearchResponse;
import com.leafy.searchservice.model.elasticsearch.PostIndex;
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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostSearchServiceImpl implements PostSearchService {

    ElasticsearchOperations elasOps;
    ElasticSearchProperties elasProps;

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
            b.filter(f -> f.term(t -> t.field("current").value(true)));

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

        List<PostSearchResponse> results = searchHits.stream()
                .map(hit -> toPostSearchResponse(hit.getContent()))
                .toList();

        return new PageImpl<>(results, pageable, searchHits.getTotalHits());
    }

    private PostSearchResponse toPostSearchResponse(PostIndex postIndex) {
        return PostSearchResponse.builder()
                .id(postIndex.getId())
                .authorId(postIndex.getAuthorId())
                .authorName(postIndex.getAuthorName())
                .authorAvatar(postIndex.getAuthorAvatar())
                .authorRole(postIndex.getAuthorRole())
                .authorVerified(postIndex.getAuthorVerified())
                .title(postIndex.getTitle())
                .caption(postIndex.getCaption())
                .hashtags(postIndex.getHashtags())
                .postType(postIndex.getPostType())
                .upvoteCount(postIndex.getUpvoteCount())
                .commentCount(postIndex.getCommentCount())
                .uploadedAt(postIndex.getUploadedAt())
            .current(postIndex.getCurrent())
                .build();
    }
}