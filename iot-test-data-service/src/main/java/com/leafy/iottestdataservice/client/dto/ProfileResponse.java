package com.leafy.iottestdataservice.client.dto;

public record ProfileResponse(
    String id,
    String userId,
    String fullName,
    String role,
    Boolean active
) {
}
