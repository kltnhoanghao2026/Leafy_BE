package com.leafy.iotmetricscollectorservice.exception;

import com.leafy.common.dto.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class TelemetryQueryExceptionHandler {

    @ExceptionHandler(TelemetryQueryException.class)
    public ResponseEntity<ApiResponse<Void>> handleTelemetryQueryException(TelemetryQueryException exception) {
        return ResponseEntity
            .status(exception.getHttpStatus())
            .body(ApiResponse.error(exception.getCode(), exception.getMessage(), null));
    }
}
