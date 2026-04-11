package com.leafy.iotmetricscollectorservice.exception;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class TelemetryQueryExceptionHandler {

    @ExceptionHandler(TelemetryQueryException.class)
    public ResponseEntity<Map<String, Object>> handleTelemetryQueryException(TelemetryQueryException exception) {
        return ResponseEntity
            .status(exception.getHttpStatus())
            .body(Map.of("code", exception.getCode(), "message", exception.getMessage()));
    }
}
