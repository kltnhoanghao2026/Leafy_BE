package com.leafy.authservice.dto.response;

import com.leafy.common.enums.Role;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {

    String id;

    String email;

    String phoneNumber;

    Role role;

    boolean active;

    LocalDateTime createdAt;

    LocalDateTime lastModifiedAt;
}
