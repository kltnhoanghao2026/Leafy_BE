package com.leafy.searchservice.services.unified;

import com.leafy.searchservice.dto.response.UnifiedSearchResponse;

public interface UnifiedSearchService {
    UnifiedSearchResponse search(String searchTerm, int postSize, int profileSize, int planSize);
}
