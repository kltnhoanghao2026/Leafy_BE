package com.leafy.profileservice.repository;

import com.leafy.profileservice.model.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for Profile entity
 */
@Repository
public interface ProfileRepository extends MongoRepository<Profile, String> {

    /**
     * Find profile by user ID
     *
     * @param userId the user ID to search for
     * @return optional profile
     */
    Optional<Profile> findByUserId(String userId);

    /**
     * Check if profile exists for user ID
     *
     * @param userId the user ID to check
     * @return true if exists, false otherwise
     */
    boolean existsByUserId(String userId);

    /**
     * Find all active profiles with pagination
     *
     * @param pageable pagination information
     * @return page of active profiles
     */
    Page<Profile> findByActiveTrue(Pageable pageable);

    /**
     * Search profiles by full name
     *
     * @param searchTerm search term
     * @param pageable   pagination information
     * @return page of matching profiles
     */
    @Query("{ 'fullName': { '$regex': ?0, '$options': 'i' } }")
    Page<Profile> searchProfiles(String searchTerm, Pageable pageable);

    /**
     * Delete profile by user ID
     *
     * @param userId the user ID
     */
    void deleteByUserId(String userId);
}
