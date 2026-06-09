package com.leafy.notificationservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SendBulkEmailRequest {
    @NotEmpty
    List<@Email(message = "Invalid email format") String> toList;

    @NotBlank
    String subject;

    @NotBlank
    String htmlContent;
}
