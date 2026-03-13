package com.leafy.profileservice.dto.request.profile;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Request DTO for internal profile creation (service-to-service calls)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InternalCreateProfileRequest {

    @NotBlank(message = "{validation.userId.required}")
    String userId;
}
