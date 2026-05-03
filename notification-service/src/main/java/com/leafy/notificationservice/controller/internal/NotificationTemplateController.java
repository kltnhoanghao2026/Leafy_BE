package com.leafy.notificationservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.notificationservice.dto.request.CreateNotificationTemplateRequest;
import com.leafy.notificationservice.model.NotificationTemplate;
import com.leafy.notificationservice.repository.NotificationTemplateRepository;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Internal admin API for managing notification templates at runtime.
 *
 * <p>All endpoints require {@code SERVICE} or {@code ADMIN} role — not accessible
 * by regular users through the gateway.
 */
@RestController
@RequestMapping("/internal/notification-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationTemplateController {

    NotificationTemplateRepository templateRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationTemplate>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(templateRepository.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationTemplate>> create(
            @Valid @RequestBody CreateNotificationTemplateRequest request) {

        LocalDateTime now = LocalDateTime.now();
        NotificationTemplate template = NotificationTemplate.builder()
                .type(request.getType())
                .channels(request.getChannels())
                .locale(request.getLocale())
                .titleTemplate(request.getTitleTemplate())
                .bodyTemplate(request.getBodyTemplate())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return ResponseEntity.ok(ApiResponse.success(templateRepository.save(template)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationTemplate>> update(
            @PathVariable String id,
            @Valid @RequestBody CreateNotificationTemplateRequest request) {

        return templateRepository.findById(id)
                .map(existing -> {
                    existing.setType(request.getType());
                    existing.setChannels(request.getChannels());
                    existing.setLocale(request.getLocale());
                    existing.setTitleTemplate(request.getTitleTemplate());
                    existing.setBodyTemplate(request.getBodyTemplate());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return ResponseEntity.ok(ApiResponse.success(templateRepository.save(existing)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable String id) {
        templateRepository.findById(id).ifPresent(t -> {
            t.setActive(false);
            t.setUpdatedAt(LocalDateTime.now());
            templateRepository.save(t);
        });
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
