package com.leafy.profileservice.repository;

import com.leafy.common.enums.ProfileRole;
import com.leafy.profileservice.model.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Custom repository fragment for dynamic profile filtering
 */
public interface ProfileRepositoryCustom {

    /**
     * Find profiles with optional filters and optional full-text search.
     *
     * @param searchTerm partial match against fullName and specialty (nullable = no filter)
     * @param role       exact role filter (nullable = no filter)
     * @param active     active flag filter (nullable = no filter)
     * @param isVerified verified flag filter (nullable = no filter)
     * @param pageable   pagination information
     * @return page of matching profiles
     */
    Page<Profile> findProfilesFiltered(
            String searchTerm,
            ProfileRole role,
            Boolean active,
            Boolean isVerified,
            Pageable pageable);
}
