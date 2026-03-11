package com.leafy.profileservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * DTO representing the response from auth-service's UserController
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {

    String id;
    String email;
    String phoneNumber;
    boolean active;

}
