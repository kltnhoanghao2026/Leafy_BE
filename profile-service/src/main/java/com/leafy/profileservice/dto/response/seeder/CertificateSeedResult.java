package com.leafy.profileservice.dto.response.seeder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the certificate (approval-request) seeder
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateSeedResult {

    /** Number of existing PENDING approval requests deleted before seeding */
    private int deletedPendingCount;

    /** Number of new ApprovalRequest documents created */
    private int seededRequestCount;

    /** Total number of individual Certificate objects embedded across all requests */
    private int seededCertificateCount;

    /** Number of distinct profiles that received at least one approval request */
    private int sourceProfileCount;
}
