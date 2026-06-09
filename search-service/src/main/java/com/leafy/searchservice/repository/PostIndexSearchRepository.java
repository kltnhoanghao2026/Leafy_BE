package com.leafy.searchservice.repository;

import com.leafy.searchservice.model.elasticsearch.PostIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostIndexSearchRepository extends ElasticsearchRepository<PostIndex, String> {
}