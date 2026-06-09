package com.leafy.authservice.controller;

import com.leafy.authservice.dto.response.InternalAccountResponse;
import com.leafy.authservice.dto.response.UserResponse;
import com.leafy.authservice.service.user.UserService;
import com.leafy.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
@Slf4j
public class InternalAccountController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<InternalAccountResponse>> getAccountByUserId(@PathVariable String userId) {
        log.info("GET /internal/accounts/{} - Getting internal account view", userId);

        UserResponse user = userService.getUserById(userId);
        InternalAccountResponse response = InternalAccountResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
