package com.leafy.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        @JsonProperty("code") int code,
        @JsonProperty("message") String message,
        @JsonProperty("data") T data,
        @JsonProperty("errors") Map<String, String> errors) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(1000, "Successful", data, null);
    }

    public static <T> ApiResponse<T> successWithoutData() {
        return new ApiResponse<>(1000, "Successful", null, null);
    }

    public static <T> ApiResponse<T> error(int code, String message,Map<String, String> errors) {
        return new ApiResponse<>(code, message, null, errors);
    }
}
