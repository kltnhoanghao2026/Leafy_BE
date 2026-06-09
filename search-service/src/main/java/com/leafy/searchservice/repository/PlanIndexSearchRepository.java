package com.leafy.searchservice.repository;

import com.leafy.searchservice.model.elasticsearch.PlanIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanIndexSearchRepository extends ElasticsearchRepository<PlanIndex, String> {
}
