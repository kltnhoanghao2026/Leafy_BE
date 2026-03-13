package com.leafy.profileservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Certificate sub-model
 * Embedded within a Profile to track user certifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String title;
    private String issuedBy;
    private String proofUrl;
    private LocalDate issueDate;

    @Builder.Default
    private boolean expired = false;
}
