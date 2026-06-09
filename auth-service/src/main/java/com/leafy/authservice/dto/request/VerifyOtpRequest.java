package com.leafy.authservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Verify OTP request DTO
 * Used for step 2 of 2-step registration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VerifyOtpRequest {

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    String email;

    @NotBlank(message = "{validation.otp.required}")
    @Size(min = 6, max = 6, message = "{validation.otp.pattern}")
    @Pattern(regexp = "^[0-9]{6}$", message = "{validation.otp.pattern}")
    String otp;
}
