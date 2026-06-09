package com.leafy.searchservice.services.planindex;

import com.leafy.searchservice.dto.response.PlanSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlanSearchService {

    Page<PlanSearchResponse> searchPlans(
            String keyword,
            String severityLevel,
            String urgency,
            Boolean isPublic,
            Pageable pageable
    );
}
