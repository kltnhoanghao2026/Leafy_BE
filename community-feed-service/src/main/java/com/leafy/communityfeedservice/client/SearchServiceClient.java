package com.leafy.communityfeedservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "search-service")
public interface SearchServiceClient {

    @PostMapping("/internal/posts/reset")
    void resetPostIndex();
}
