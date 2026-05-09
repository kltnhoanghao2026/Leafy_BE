package com.leafy.iottestdataservice.client.dto;

public record ApiResponse<T>(
    Integer code,
    String message,
    T data
) {
}
