package com.leafy.authservice.client;

import com.leafy.authservice.client.dto.EmailRequest;
import com.leafy.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for notification-service
 * Handles email notifications
 */
@FeignClient(name = "notification-service", path = "/internal")
public interface NotificationClient {
    
    /**
     * Send an email via notification service internal endpoint
     *
     * @param emailRequest the email request
     * @return API response
     */
    @PostMapping("/mailing/send")
    ApiResponse<Void> sendEmail(@RequestBody EmailRequest emailRequest);
}
