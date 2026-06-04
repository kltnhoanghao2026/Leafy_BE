package com.leafy.searchservice.services.profileindex;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.leafy.common.enums.ProfileRole;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.searchservice.config.ElasticSearchProperties;
import com.leafy.searchservice.dto.response.ProfileResponse;
import com.leafy.searchservice.model.elasticsearch.ProfileIndex;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileSearchServiceImpl implements ProfileSearchService {

    ElasticsearchOperations elasOps;
    ElasticSearchProperties elasProps;

    @Override
    public Page<ProfileResponse> searchProfile(
            String keyword,
            ProfileRole role,
            Boolean isVerified,
            String specialty,
            Pageable pageable
    ) {
        String searchQuery = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String specialtyFilter = StringUtils.hasText(specialty) ? specialty.trim() : null;
        String currentUserId = resolveCurrentUserId();

        Query query = Query.of(q ->
                q.bool(b -> {
                    if (searchQuery != null) {
                        b.should(s -> s.term(t ->
                                t.field("phoneNumber").value(searchQuery)
                        ));

                        b.should(s -> s.match(m ->
                                m.field("fullName")
                                        .query(searchQuery)
                                        .boost(0.5f)
                        ));

                        b.should(s -> s.multiMatch(mm ->
                                mm.fields("fullName", "fullName.fuzzy")
                                        .query(searchQuery)
                                        .fuzziness("AUTO")
                                        .prefixLength(1)
                                        .maxExpansions(50)
                                        .type(TextQueryType.BestFields)
                                        .boost(1.5f)
                        ));

                        // Allow keyword search to match expert specialty & bio as well
                        b.should(s -> s.match(m ->
                                m.field("specialty")
                                        .query(searchQuery)
                                        .boost(1.0f)
                        ));

                        b.should(s -> s.match(m ->
                                m.field("bio")
                                        .query(searchQuery)
                                        .boost(0.6f)
                        ));

                        b.minimumShouldMatch("1");
                    }

                    b.filter(f -> f.term(t -> t.field("active").value(true)));

                    if (role != null) {
                        b.filter(f -> f.term(t -> t.field("role").value(role.name())));
                    }

                    if (isVerified != null) {
                        b.filter(f -> f.term(t -> t.field("isVerified").value(isVerified)));
                    }

                    if (specialtyFilter != null) {
                        b.filter(f -> f.match(m -> m.field("specialty").query(specialtyFilter)));
                    }

                    if (StringUtils.hasText(currentUserId)) {
                        b.mustNot(mn -> mn.term(t -> t.field("userId").value(currentUserId)));
                    }

                    return b;
                })
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable)
                .build();

        SearchHits<ProfileIndex> searchHits = elasOps.search(
                nativeQuery,
                ProfileIndex.class,
                IndexCoordinates.of(elasProps.getProfileAlias())
        );

        List<ProfileResponse> results = searchHits.stream()
                .map(hit -> toProfileResponse(hit.getContent()))
                .toList();

        return new PageImpl<>(results, pageable, searchHits.getTotalHits());
    }

    private ProfileResponse toProfileResponse(ProfileIndex profileIndex) {
        return ProfileResponse.builder()
                .id(profileIndex.getId())
                .userId(profileIndex.getUserId())
                .fullName(profileIndex.getFullName())
                .profilePicture(profileIndex.getProfilePicture())
                .avatar(profileIndex.getAvatar())
                .role(profileIndex.getRole())
                .specialty(profileIndex.getSpecialty())
                .isVerified(profileIndex.getIsVerified())
                .bio(profileIndex.getBio())
                .addressLine(profileIndex.getAddressLine())
                .provinceCode(profileIndex.getProvinceCode())
                .districtCode(profileIndex.getDistrictCode())
                .wardCode(profileIndex.getWardCode())
                .latitude(profileIndex.getLatitude())
                .longitude(profileIndex.getLongitude())
                .build();
    }

    private String resolveCurrentUserId() {
        try {
            return ServiceSecurityUtils.getCurrentUserId();
        } catch (Exception ex) {
            log.debug("Cannot resolve current user id from security context", ex);
            return null;
        }
    }
}
