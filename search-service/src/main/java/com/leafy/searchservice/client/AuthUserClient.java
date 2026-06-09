package com.leafy.searchservice.client;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.client.dto.AuthUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service", path = "/internal/accounts")
public interface AuthUserClient {

    @GetMapping("/{userId}")
    ApiResponse<AuthUserResponse> getUserById(@PathVariable("userId") String userId);
}
