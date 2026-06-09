package com.leafy.searchservice.services.profileindex;

import com.leafy.common.enums.ProfileRole;
import com.leafy.searchservice.dto.response.ProfileResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProfileSearchService {

    Page<ProfileResponse> searchProfile(
            String keyword,
            ProfileRole role,
            Boolean isVerified,
            String specialty,
            Pageable pageable
    );
}
