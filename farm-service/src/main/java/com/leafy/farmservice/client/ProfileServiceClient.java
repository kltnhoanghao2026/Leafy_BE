package com.leafy.farmservice.client;

import com.leafy.farmservice.client.dto.ExternalApiResponse;
import com.leafy.farmservice.client.dto.PagedResponse;
import com.leafy.farmservice.client.dto.ProfileSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for communicating with profile-service.
 *
 * Security headers are injected automatically by FeignSecurityInterceptor
 * from the common module.
 */
@FeignClient(name = "profile-service")
public interface ProfileServiceClient {

    @GetMapping("/profiles/active")
    ExternalApiResponse<PagedResponse<ProfileSummary>> getActiveProfiles(
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam("sortBy") String sortBy,
            @RequestParam("sortDir") String sortDir);
}
