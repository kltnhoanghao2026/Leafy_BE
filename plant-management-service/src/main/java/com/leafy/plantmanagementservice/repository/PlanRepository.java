package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.Plan;
import com.leafy.plantmanagementservice.model.enums.PlanSourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanRepository extends MongoRepository<Plan, String> {


    Page<Plan> findByOwnerId(String ownerId, Pageable pageable);

    Page<Plan> findByCreatorId(String creatorId, Pageable pageable);

    Page<Plan> findByOwnerIdOrCreatorId(String ownerId, String creatorId, Pageable pageable);


    // ── Public plan queries ───────────────────────────────────────────────────

    Page<Plan> findByIsPublicTrue(Pageable pageable);

    Page<Plan> findByIsPublicTrueAndSourceType(PlanSourceType sourceType, Pageable pageable);

    /**
     * Public plans whose diseaseName or planName contains the search term (case-insensitive).
     */
    @Query("{isPublic: true, $or: [{diseaseName: {$regex: ?0, $options: 'i'}}, {planName: {$regex: ?0, $options: 'i'}}]}")
    Page<Plan> findPublicBySearch(String search, Pageable pageable);

    /**
     * Public plans filtered by sourceType with search term.
     */
    @Query("{isPublic: true, sourceType: ?0, $or: [{diseaseName: {$regex: ?1, $options: 'i'}}, {planName: {$regex: ?1, $options: 'i'}}]}")
    Page<Plan> findPublicBySearchAndSourceType(PlanSourceType sourceType, String search, Pageable pageable);

    /**
     * My plans (owner or creator) filtered by diseaseName/planName search.
     */
    @Query("{$or: [{ownerId: ?0}, {creatorId: ?0}], $or: [{diseaseName: {$regex: ?1, $options: 'i'}}, {planName: {$regex: ?1, $options: 'i'}}]}")
    Page<Plan> findByOwnerOrCreatorAndSearch(String profileId, String search, Pageable pageable);

    /**
     * My plans (owner or creator) filtered by sourceType and search term.
     */
    @Query("{$or: [{ownerId: ?0}, {creatorId: ?0}], sourceType: ?1, $or: [{diseaseName: {$regex: ?2, $options: 'i'}}, {planName: {$regex: ?2, $options: 'i'}}]}")
    Page<Plan> findByOwnerOrCreatorAndSearchAndSourceType(String profileId, PlanSourceType sourceType, String search, Pageable pageable);

    /**
     * My plans (owner or creator) filtered by sourceType only (no search).
     */
    Page<Plan> findByOwnerIdOrCreatorIdAndSourceType(String ownerId, String creatorId, PlanSourceType sourceType, Pageable pageable);

    long countByOwnerIdAndActiveTrue(String ownerId);
}
