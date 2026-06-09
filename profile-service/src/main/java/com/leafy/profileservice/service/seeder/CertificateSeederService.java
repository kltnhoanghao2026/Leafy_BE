package com.leafy.profileservice.service.seeder;

import com.leafy.profileservice.dto.response.seeder.CertificateSeedResult;

public interface CertificateSeederService {

    /**
     * Delete all existing PENDING approval requests, then create {@code requestCount}
     * new approval requests distributed across real profiles.
     *
     * @param requestCount number of approval-request documents to create (default: 20)
     * @param certsPerRequest certificates embedded in each request (default: 2)
     * @return seeding statistics
     */
    CertificateSeedResult reseed(Integer requestCount, Integer certsPerRequest);
}
