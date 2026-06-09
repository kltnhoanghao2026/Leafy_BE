package com.leafy.profileservice.repository;

import com.leafy.profileservice.model.ApprovalRequest;
import com.leafy.profileservice.model.enums.CertificateStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ApprovalRequest entity operations
 */
@Repository
public interface ApprovalRequestRepository extends MongoRepository<ApprovalRequest, String> {

    /**
     * Find approval requests by profile ID
     *
     * @param profileId the profile ID
     * @return list of matching approval requests
     */
    List<ApprovalRequest> findByProfileId(String profileId);

    /**
     * Delete all approval requests associated with a profile
     *
     * @param profileId the profile ID
     */
    void deleteByProfileId(String profileId);

    /**
     * Find all approval requests by specific status
     * 
     * @param status the status to query for
     * @return list of matching approval requests
     */
    List<ApprovalRequest> findByStatus(CertificateStatus status);

    /**
     * Find all approval requests whose status is NOT the given status
     *
     * @param status the status to exclude
     * @return list of matching approval requests
     */
    List<ApprovalRequest> findByStatusNot(CertificateStatus status);
}
