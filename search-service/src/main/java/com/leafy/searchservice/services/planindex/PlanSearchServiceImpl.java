package com.leafy.searchservice.services.planindex;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.leafy.searchservice.config.ElasticSearchProperties;
import com.leafy.searchservice.dto.response.AuthorInfoResponse;
import com.leafy.searchservice.dto.response.PlanSearchResponse;
import com.leafy.searchservice.model.elasticsearch.PlanIndex;
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
public class PlanSearchServiceImpl implements PlanSearchService {

    ElasticsearchOperations elasOps;
    ElasticSearchProperties elasProps;
    ProfileIndexSearchRepository profileIndexSearchRepository;

    @Override
    public Page<PlanSearchResponse> searchPlans(
            String keyword,
            String severityLevel,
            String urgency,
            Boolean isPublic,
            Pageable pageable
    ) {
        if (!StringUtils.hasText(keyword)) {
            return searchAllPlans(severityLevel, urgency, isPublic, pageable);
        }

        String searchQuery = keyword.trim();
        String normalizedQuery = searchQuery.toLowerCase();
        String normalizedSeverity = StringUtils.hasText(severityLevel) ? severityLevel.trim().toUpperCase() : null;
        String normalizedUrgency = StringUtils.hasText(urgency) ? urgency.trim().toUpperCase() : null;

        Query query = Query.of(q -> q.bool(b -> {
            // Vietnamese-aware main text match (edge-ngram + ICU analyzers)
            b.should(s -> s.multiMatch(mm -> mm
                    .fields(
                            "planName^3",
                            "diseaseName^3",
                            "question^2",
                            "creatorName^2",
                            "successIndicators",
                            "source"
                    )
                    .query(searchQuery)
                    .boost(0.5f)
            ));

            // Fuzzy fallback via .fuzzy subfields
            b.should(s -> s.multiMatch(mm -> mm
                    .fields(
                            "planName.fuzzy^3",
                            "diseaseName.fuzzy^3",
                            "question.fuzzy^2",
                            "creatorName.fuzzy^2",
                            "successIndicators.fuzzy",
                            "source.fuzzy"
                    )
                    .query(searchQuery)
                    .fuzziness("AUTO")
                    .prefixLength(1)
                    .maxExpansions(50)
                    .type(TextQueryType.BestFields)
                    .boost(1.5f)
            ));

            // Exact match on ID
            b.should(s -> s.term(t -> t.field("id").value(searchQuery)));

            // Exact match on keyword fields (lowercase-normalized)
            b.should(s -> s.term(t -> t.field("planName.keyword").value(normalizedQuery)));
            b.should(s -> s.term(t -> t.field("diseaseName.keyword").value(normalizedQuery)));
            b.should(s -> s.term(t -> t.field("creatorName.keyword").value(normalizedQuery)));

            // Match on list fields
            b.should(s -> s.term(t -> t.field("requiredInputs").value(searchQuery)));
            b.should(s -> s.term(t -> t.field("safetyWarnings").value(searchQuery)));

            b.minimumShouldMatch("1");

            // Filters
            if (normalizedSeverity != null) {
                b.filter(f -> f.term(t -> t.field("severityLevel").value(normalizedSeverity)));
            }

            if (normalizedUrgency != null) {
                b.filter(f -> f.term(t -> t.field("urgency").value(normalizedUrgency)));
            }

            if (isPublic != null) {
                b.filter(f -> f.term(t -> t.field("isPublic").value(isPublic)));
            }

            return b;
        }));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable)
                .build();

        SearchHits<PlanIndex> searchHits = elasOps.search(
                nativeQuery,
                PlanIndex.class,
                IndexCoordinates.of(elasProps.getPlanAlias())
        );

        List<PlanIndex> plans = searchHits.stream()
                .map(hit -> hit.getContent())
                .toList();

        Map<String, AuthorInfoResponse> creatorInfoMap = fetchCreatorInfoMap(plans);

        List<PlanSearchResponse> results = plans.stream()
                .map(plan -> toPlanSearchResponse(plan, creatorInfoMap))
                .toList();

        return new PageImpl<>(results, pageable, searchHits.getTotalHits());
    }

    private Map<String, AuthorInfoResponse> fetchCreatorInfoMap(List<PlanIndex> plans) {
        Set<String> creatorIds = plans.stream()
                .map(PlanIndex::getCreatorId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (creatorIds.isEmpty()) {
            return Map.of();
        }

        return StreamSupport.stream(profileIndexSearchRepository.findAllById(creatorIds).spliterator(), false)
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

    private PlanSearchResponse toPlanSearchResponse(PlanIndex planIndex, Map<String, AuthorInfoResponse> creatorInfoMap) {
        AuthorInfoResponse creatorInfo = creatorInfoMap.get(planIndex.getCreatorId());
        return PlanSearchResponse.builder()
                .id(planIndex.getId())
                .creatorId(planIndex.getCreatorId())
                .ownerId(planIndex.getOwnerId())
                .planName(planIndex.getPlanName())
                .diseaseName(planIndex.getDiseaseName())
                .confidenceScore(planIndex.getConfidenceScore())
                .severityLevel(planIndex.getSeverityLevel())
                .urgency(planIndex.getUrgency())
                .requiredInputs(planIndex.getRequiredInputs())
                .safetyWarnings(planIndex.getSafetyWarnings())
                .successIndicators(planIndex.getSuccessIndicators())
                .estimatedCost(planIndex.getEstimatedCost())
                .source(planIndex.getSource())
                .isPublic(planIndex.getIsPublic())
                .sourceType(planIndex.getSourceType())
                .eventCount(planIndex.getEventCount())
                .applyCount(planIndex.getApplyCount())
                .creatorInfo(creatorInfo)
                .createdAt(planIndex.getCreatedAt())
                .build();
    }

    private Page<PlanSearchResponse> searchAllPlans(
            String severityLevel,
            String urgency,
            Boolean isPublic,
            Pageable pageable
    ) {
        String normalizedSeverity = StringUtils.hasText(severityLevel) ? severityLevel.trim().toUpperCase() : null;
        String normalizedUrgency = StringUtils.hasText(urgency) ? urgency.trim().toUpperCase() : null;

        Query query = Query.of(q -> q.bool(b -> {
            if (normalizedSeverity != null) {
                b.filter(f -> f.term(t -> t.field("severityLevel").value(normalizedSeverity)));
            }
            if (normalizedUrgency != null) {
                b.filter(f -> f.term(t -> t.field("urgency").value(normalizedUrgency)));
            }
            if (isPublic != null) {
                b.filter(f -> f.term(t -> t.field("isPublic").value(isPublic)));
            }
            return b;
        }));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable)
                .build();

        SearchHits<PlanIndex> searchHits = elasOps.search(
                nativeQuery,
                PlanIndex.class,
                IndexCoordinates.of(elasProps.getPlanAlias())
        );

        List<PlanIndex> plans = searchHits.stream()
                .map(hit -> hit.getContent())
                .toList();

        Map<String, AuthorInfoResponse> creatorInfoMap = fetchCreatorInfoMap(plans);

        List<PlanSearchResponse> results = plans.stream()
                .map(plan -> toPlanSearchResponse(plan, creatorInfoMap))
                .toList();

        return new PageImpl<>(results, pageable, searchHits.getTotalHits());
    }
}
