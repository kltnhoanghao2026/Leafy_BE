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
            HttpStatus.CONFLICT,
            4608,
            "Device UID already exists: " + deviceUid
        );
    }

    public static TelemetryQueryException duplicateDeviceCode(String deviceCode) {
        return new TelemetryQueryException(
            HttpStatus.CONFLICT,
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
            HttpStatus.CONFLICT,
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

    public static TelemetryQueryException alertRuleNotFound(UUID ruleId) {
        return new TelemetryQueryException(
            HttpStatus.NOT_FOUND,
            4618,
            "Alert rule not found: " + ruleId
        );
    }

    public static TelemetryQueryException invalidAlertRuleScope() {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4619,
            "Alert rule must target at least one scope: device, zone, or farm plot"
        );
    }

    public static TelemetryQueryException invalidAlertRuleThresholds() {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4620,
            "Alert rule thresholds are invalid"
        );
    }

    public static TelemetryQueryException invalidAlertRuleSeverity(String severity) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4621,
            "Unsupported alert rule severity: " + severity
        );
    }

    public static TelemetryQueryException missingAlertRuleSensorType(UUID sensorTypeId) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4622,
            "Sensor type is required and must exist: " + sensorTypeId
        );
    }

    public static TelemetryQueryException invalidAlertRuleCooldown(Integer cooldownMinutes) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4623,
            "Alert rule cooldown must be null or greater than or equal to 0: " + cooldownMinutes
        );
    }

    public static TelemetryQueryException invalidAlertRuleEnabledValue() {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4624,
            "Alert rule enabled value must not be null"
        );
    }

    public static TelemetryQueryException invalidAlertEventSortField(String sortBy) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4625,
            "Unsupported alert event sort field: " + sortBy
        );
    }

    public static TelemetryQueryException invalidAlertRuleSortField(String sortBy) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4626,
            "Unsupported alert rule sort field: " + sortBy
        );
    }

    public static TelemetryQueryException invalidSortDirection(String sortDir) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4627,
            "Unsupported sort direction: " + sortDir
        );
    }

    public static TelemetryQueryException invalidDeviceSortField(String sortBy) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4628,
            "Unsupported device sort field: " + sortBy
        );
    }

    public static TelemetryQueryException inactiveDevice(UUID deviceId) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4629,
            "IoT device is inactive: " + deviceId
        );
    }

    public static TelemetryQueryException unclaimedDevice(UUID deviceId) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4630,
            "IoT device must be claimed before camera capture: " + deviceId
        );
    }

    public static TelemetryQueryException cameraCaptureCommandFailed(UUID deviceId) {
        return new TelemetryQueryException(
            HttpStatus.BAD_GATEWAY,
            4631,
            "Failed to send camera capture command for device: " + deviceId
        );
    }

    public static TelemetryQueryException mediaEventNotFound(UUID mediaEventId) {
        return new TelemetryQueryException(
            HttpStatus.NOT_FOUND,
            4632,
            "Device media event not found: " + mediaEventId
        );
    }

    public static TelemetryQueryException cameraScheduleNotFound(UUID scheduleId) {
        return new TelemetryQueryException(
            HttpStatus.NOT_FOUND,
            4633,
            "Device camera schedule not found: " + scheduleId
        );
    }

    public static TelemetryQueryException invalidCameraSchedule(String message) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4634,
            "Invalid camera schedule: " + message
        );
    }

    public static TelemetryQueryException invalidDeviceUpdate(String message) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4635,
            "Invalid device update: " + message
        );
    }

    public static TelemetryQueryException deviceAccessDenied(UUID deviceId) {
        return new TelemetryQueryException(
            HttpStatus.FORBIDDEN,
            4636,
            "Access denied for IoT device: " + deviceId
        );
    }

    public static TelemetryQueryException scopeAccessDenied(String scopeType, String scopeId) {
        return new TelemetryQueryException(
            HttpStatus.FORBIDDEN,
            4637,
            "Access denied for IoT scope " + scopeType + ": " + scopeId
        );
    }

    public static TelemetryQueryException deviceUidRequired() {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4638,
            "IOT_DEVICE_UID_REQUIRED: Device identifier is required."
        );
    }

    public static TelemetryQueryException invalidDeviceUid(String deviceUid) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4639,
            "IOT_DEVICE_UID_INVALID: Device identifier format is invalid: " + deviceUid
        );
    }

    public static TelemetryQueryException deviceCodeRequired() {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4640,
            "IOT_DEVICE_CODE_REQUIRED: Device code is required."
        );
    }

    public static TelemetryQueryException invalidDeviceCode(String deviceCode) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4641,
            "IOT_DEVICE_CODE_INVALID: Device code format is invalid: " + deviceCode
        );
    }

    public static TelemetryQueryException invalidDeviceType(String deviceType) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4642,
            "IOT_DEVICE_TYPE_INVALID: Device type format is invalid: " + deviceType
        );
    }

    public static TelemetryQueryException invalidDeviceName(String message) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4643,
            "IOT_DEVICE_NAME_INVALID: " + message
        );
    }

    public static TelemetryQueryException farmPlotRequired() {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4644,
            "IOT_FARM_PLOT_REQUIRED: Farm plot is required."
        );
    }

    public static TelemetryQueryException farmZoneRequired() {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4645,
            "IOT_FARM_ZONE_REQUIRED: Farm zone is required."
        );
    }

    public static TelemetryQueryException invalidFarmPlot(String farmPlotId) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4646,
            "IOT_FARM_PLOT_INVALID: Farm plot id format is invalid: " + farmPlotId
        );
    }

    public static TelemetryQueryException invalidFarmZone(String zoneId) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4647,
            "IOT_FARM_ZONE_INVALID: Farm zone id format is invalid: " + zoneId
        );
    }

    public static TelemetryQueryException claimCodeRequired() {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4648,
            "IOT_CLAIM_CODE_REQUIRED: Claim code is required."
        );
    }

    public static TelemetryQueryException invalidClaimCodeFormat(String claimCode) {
        return new TelemetryQueryException(
            HttpStatus.BAD_REQUEST,
            4649,
            "IOT_CLAIM_CODE_INVALID: Claim code format is invalid: " + claimCode
        );
    }

    public static TelemetryQueryException deviceCodeConflict(String deviceCode) {
        return new TelemetryQueryException(
            HttpStatus.CONFLICT,
            4650,
            "IOT_DEVICE_CODE_CONFLICT: Device code belongs to another device: " + deviceCode
        );
    }

    public static TelemetryQueryException deviceUidConflict(String deviceUid) {
        return new TelemetryQueryException(
            HttpStatus.CONFLICT,
            4651,
            "IOT_DEVICE_UID_CONFLICT: Device identifier conflicts with another device: " + deviceUid
        );
    }
}
