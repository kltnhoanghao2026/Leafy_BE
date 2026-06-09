package com.leafy.searchservice.services.postindex;

import com.leafy.searchservice.dto.response.PostSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostSearchService {

    Page<PostSearchResponse> searchPosts(
            String keyword,
            String postType,
            String authorId,
            Pageable pageable
    );
}