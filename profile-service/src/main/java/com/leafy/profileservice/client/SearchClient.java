package com.leafy.profileservice.client;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.client.dto.SpringPageDto;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "search-service", contextId = "searchClient")
public interface SearchClient {

    @GetMapping("/internal/profiles/search")
    ApiResponse<SpringPageDto<ProfileResponse>> searchProfiles(
            @RequestParam("searchTerm") String searchTerm,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "isVerified", required = false) Boolean isVerified,
            @RequestParam(value = "specialty", required = false) String specialty,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "DESC") String sortDir
    );
}
