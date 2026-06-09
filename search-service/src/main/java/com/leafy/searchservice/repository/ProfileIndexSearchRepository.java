package com.leafy.searchservice.repository;

import com.leafy.searchservice.model.elasticsearch.ProfileIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfileIndexSearchRepository extends ElasticsearchRepository<ProfileIndex, String> {
}
