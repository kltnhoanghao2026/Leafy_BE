package com.leafy.authservice.dto.request;

import com.leafy.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserUpdateRequest {

    @Email(message = "{validation.email.invalid}")
    String email;

    @Pattern(regexp = "^(\\+84|0)[0-9]{9}$", message = "{validation.phoneNumber.pattern}")
    String phoneNumber;

    String password;

    Role role;

    Boolean active;
}
