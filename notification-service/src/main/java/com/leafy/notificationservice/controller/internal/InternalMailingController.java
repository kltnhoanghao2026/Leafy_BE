package com.leafy.notificationservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.notificationservice.controller.MailingController;
import com.leafy.notificationservice.dto.request.EmailRequest;
import com.leafy.notificationservice.dto.response.EmailResponse;
import com.leafy.notificationservice.service.mail.MailingService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/mailing")
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalMailingController {

    MailingService mailingService;

    /**
     * Internal endpoint to send email (no authentication required)
     * Used by internal microservices
     *
     * @param request the email request
     * @return the email response
     */
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<EmailResponse>> sendInternalEmail(@Valid @RequestBody EmailRequest request) {
        log.info("POST /internal/mailing/send - Sending internal email to: {}",
                request.getTo() != null && !request.getTo().isEmpty() ? request.getTo().get(0) : "N/A");


        EmailResponse response = mailingService.sendEmail(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(5001, response.getMessage(), null));
        }
    }
}
