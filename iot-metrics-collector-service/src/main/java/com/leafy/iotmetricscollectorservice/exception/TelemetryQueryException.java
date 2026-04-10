package com.leafy.iotmetricscollectorservice.exception;

import java.util.UUID;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TelemetryQueryException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final int code;

    private TelemetryQueryException(HttpStatus httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public static TelemetryQueryException deviceNotFound(UUID deviceId) {
        return new TelemetryQueryException(
            HttpStatus.NOT_FOUND,
            4601,
            "IoT device not found: " + deviceId
        );
    }

    public static TelemetryQueryException unknownSensorCode(String sensorCode) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4602,
            "Unknown sensor code: " + sensorCode
        );
    }

    public static TelemetryQueryException invalidChartRange(String range) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4603,
            "Unsupported chart range: " + range
        );
    }

    public static TelemetryQueryException alertEventNotFound(UUID alertEventId) {
        return new TelemetryQueryException(
            HttpStatus.NOT_FOUND,
            4604,
            "Alert event not found: " + alertEventId
        );
    }

    public static TelemetryQueryException invalidAlertSearchWindow() {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4605,
            "Invalid alert search window"
        );
    }

    public static TelemetryQueryException cannotAcknowledgeAlert(UUID alertEventId, String status) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4606,
            "Cannot acknowledge alert " + alertEventId + " in status " + status
        );
    }

    public static TelemetryQueryException cannotResolveAlert(UUID alertEventId, String status) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4607,
            "Cannot resolve alert " + alertEventId + " in status " + status
        );
    }

    public static TelemetryQueryException duplicateDeviceUid(String deviceUid) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4608,
            "Device UID already exists: " + deviceUid
        );
    }

    public static TelemetryQueryException duplicateDeviceCode(String deviceCode) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4609,
            "Device code already exists: " + deviceCode
        );
    }

    public static TelemetryQueryException deviceNotFoundByUid(String deviceUid) {
        return new TelemetryQueryException(
            HttpStatus.NOT_FOUND,
            4610,
            "IoT device not found: " + deviceUid
        );
    }

    public static TelemetryQueryException invalidClaimCode(String deviceUid) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4611,
            "Invalid claim code for device: " + deviceUid
        );
    }

    public static TelemetryQueryException expiredClaimCode(String deviceUid) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4612,
            "Claim code has expired for device: " + deviceUid
        );
    }

    public static TelemetryQueryException deviceAlreadyClaimed(UUID deviceId) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4613,
            "Device already claimed: " + deviceId
        );
    }

    public static TelemetryQueryException invalidClaimState(UUID deviceId, String state) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4614,
            "Invalid claim state for device " + deviceId + ": " + state
        );
    }

    public static TelemetryQueryException invalidDeviceConfigIntervals() {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4615,
            "Invalid device config intervals"
        );
    }

    public static TelemetryQueryException invalidDeviceConfigState(UUID deviceId, String state) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4616,
            "Invalid device config state for device " + deviceId + ": " + state
        );
    }

    public static TelemetryQueryException deviceConfigPushFailed(UUID deviceId) {
        return new TelemetryQueryException(
            HttpStatus.BAD_GATEWAY,
            4617,
            "Failed to push config for device: " + deviceId
        );
    }
}
