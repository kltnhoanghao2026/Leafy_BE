package com.leafy.profileservice.repository;

import com.leafy.common.enums.ProfileRole;
import com.leafy.profileservice.model.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom implementation for dynamic profile filtering using MongoTemplate.
 * Spring Data picks this up automatically as a fragment for ProfileRepository.
 */
@RequiredArgsConstructor
public class ProfileRepositoryCustomImpl implements ProfileRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Profile> findProfilesFiltered(
            String searchTerm,
            ProfileRole role,
            Boolean active,
            Boolean isVerified,
            Pageable pageable) {

        List<Criteria> criteriaList = new ArrayList<>();

        if (searchTerm != null && !searchTerm.isBlank()) {
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("fullName").regex(searchTerm, "i"),
                    Criteria.where("specialty").regex(searchTerm, "i")
            ));
        }
        if (role != null) {
            criteriaList.add(Criteria.where("role").is(role));
        }
        if (active != null) {
            criteriaList.add(Criteria.where("active").is(active));
        }
        if (isVerified != null) {
            criteriaList.add(Criteria.where("isVerified").is(isVerified));
        }

        Criteria finalCriteria = criteriaList.isEmpty()
                ? new Criteria()
                : new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));

        Query query = new Query(finalCriteria).with(pageable);
        Query countQuery = new Query(finalCriteria);

        List<Profile> results = mongoTemplate.find(query, Profile.class);
        long total = mongoTemplate.count(countQuery, Profile.class);

        return new PageImpl<>(results, pageable, total);
    }
}
