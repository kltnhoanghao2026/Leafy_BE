package com.leafy.profileservice.client;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.client.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for communicating with the auth-service
 */
@FeignClient(name = "auth-service", path = "/internal/users")
public interface AuthClient {

    /**
     * Get user details from auth-service
     *
     * @param userId the user ID
     * @return API response containing user details
     */
    @GetMapping("/{userId}")
    ApiResponse<UserResponse> getUserById(@PathVariable("userId") String userId);

}
