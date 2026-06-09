package com.leafy.profileservice.client;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.dto.request.sync.ProfileSyncBulkRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "search-service")
public interface SearchSyncClient {

    @PostMapping("/internal/search/sync/bulk")
    ApiResponse<Object> bulkSyncProfiles(@RequestBody ProfileSyncBulkRequest request);
}
