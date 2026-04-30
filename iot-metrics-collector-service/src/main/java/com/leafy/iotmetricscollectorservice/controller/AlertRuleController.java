package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.alert_rule.AlertRuleResponse;
import com.leafy.iotmetricscollectorservice.dto.alert_rule.CreateAlertRuleRequest;
import com.leafy.iotmetricscollectorservice.dto.alert_rule.UpdateAlertRuleEnabledRequest;
import com.leafy.iotmetricscollectorservice.dto.alert_rule.UpdateAlertRuleRequest;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.service.AlertRuleService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/iot/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    static final String USER_ID_HEADER = DeviceController.USER_ID_HEADER;

    private final AlertRuleService alertRuleService;

    @PostMapping
    public ResponseEntity<AlertRuleResponse> createRule(
        @RequestHeader(USER_ID_HEADER) String currentUserId,
        @RequestBody CreateAlertRuleRequest request
    ) {
        return ResponseEntity.ok(alertRuleService.createRule(currentUserId, request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<AlertRuleResponse>> listRules(
        @RequestHeader(USER_ID_HEADER) String currentUserId,
        @RequestParam(required = false) UUID sensorTypeId,
        @RequestParam(required = false) UUID deviceId,
        @RequestParam(required = false) String zoneId,
        @RequestParam(required = false) String farmPlotId,
        @RequestParam(required = false) Boolean enabled,
        @RequestParam(defaultValue = "0") Integer page,
        @RequestParam(defaultValue = "20") Integer size,
        @RequestParam(defaultValue = "updatedAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(
            alertRuleService.listRules(currentUserId, sensorTypeId, deviceId, zoneId, farmPlotId, enabled, page, size, sortBy, sortDir)
        );
    }

    @GetMapping("/{ruleId}")
    public ResponseEntity<AlertRuleResponse> getRule(
        @RequestHeader(USER_ID_HEADER) String currentUserId,
        @PathVariable UUID ruleId
    ) {
        return ResponseEntity.ok(alertRuleService.getRule(currentUserId, ruleId));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<AlertRuleResponse> updateRule(
        @RequestHeader(USER_ID_HEADER) String currentUserId,
        @PathVariable UUID ruleId,
        @RequestBody UpdateAlertRuleRequest request
    ) {
        return ResponseEntity.ok(alertRuleService.updateRule(currentUserId, ruleId, request));
    }

    @PatchMapping("/{ruleId}/enabled")
    public ResponseEntity<AlertRuleResponse> updateRuleEnabled(
        @RequestHeader(USER_ID_HEADER) String currentUserId,
        @PathVariable UUID ruleId,
        @RequestBody UpdateAlertRuleEnabledRequest request
    ) {
        return ResponseEntity.ok(alertRuleService.updateRuleEnabled(currentUserId, ruleId, request.getEnabled()));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(
        @RequestHeader(USER_ID_HEADER) String currentUserId,
        @PathVariable UUID ruleId
    ) {
        alertRuleService.deleteRule(currentUserId, ruleId);
        return ResponseEntity.noContent().build();
    }
}
