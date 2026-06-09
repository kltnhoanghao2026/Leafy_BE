package com.leafy.plantmanagementservice.dto.response.plan;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthorInfo {
    String id;
    String fullName;
    String avatar;
    String role;
    String specialty;
    Boolean isVerified;
}
