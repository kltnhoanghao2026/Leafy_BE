package com.leafy.profileservice.repository;

import com.leafy.profileservice.model.ConsultingDataAccessRequest;
import com.leafy.profileservice.model.enums.AccessRequestStatus;
import com.leafy.profileservice.model.enums.ConsultingDataType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ConsultingDataAccessRequest entity operations.
 */
@Repository
public interface ConsultingDataAccessRequestRepository extends MongoRepository<ConsultingDataAccessRequest, String> {

    /**
     * Find an existing request by expert, farmer, and data type.
     */
    Optional<ConsultingDataAccessRequest> findByExpertProfileIdAndFarmerProfileIdAndDataType(
            String expertProfileId, String farmerProfileId, ConsultingDataType dataType);

    /**
     * Find all pending requests for a given farmer.
     */
    Page<ConsultingDataAccessRequest> findByFarmerProfileIdAndStatus(
            String farmerProfileId, AccessRequestStatus status, Pageable pageable);

    /**
     * Find all requests by a given expert.
     */
    List<ConsultingDataAccessRequest> findByExpertProfileId(String expertProfileId);

    /**
     * Find all requests by a given expert with a specific status.
     */
    List<ConsultingDataAccessRequest> findByExpertProfileIdAndStatus(
            String expertProfileId, AccessRequestStatus status);

    /**
     * Find all requests by a given expert with a specific status (paginated).
     */
    Page<ConsultingDataAccessRequest> findByExpertProfileIdAndStatus(
            String expertProfileId, AccessRequestStatus status, Pageable pageable);

    /**
     * Check if an approved request exists for the given (expert, farmer, dataType) tuple.
     */
    boolean existsByExpertProfileIdAndFarmerProfileIdAndDataTypeAndStatus(
            String expertProfileId, String farmerProfileId, ConsultingDataType dataType, AccessRequestStatus status);

    /**
     * Find all requests with a specific status (for scheduled cleanup).
     */
    List<ConsultingDataAccessRequest> findByStatus(AccessRequestStatus status);

    /**
     * Find all PENDING requests older than the given cutoff datetime (for expiry cleanup).
     */
    List<ConsultingDataAccessRequest> findByStatusAndCreatedAtBefore(
            AccessRequestStatus status, java.time.LocalDateTime cutoff);
}
