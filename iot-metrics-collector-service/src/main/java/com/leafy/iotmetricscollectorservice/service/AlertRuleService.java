package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.alert_rule.AlertRuleResponse;
import com.leafy.iotmetricscollectorservice.dto.alert_rule.CreateAlertRuleRequest;
import com.leafy.iotmetricscollectorservice.dto.alert_rule.UpdateAlertRuleRequest;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import java.util.UUID;

public interface AlertRuleService {

    AlertRuleResponse createRule(String currentUserId, CreateAlertRuleRequest request);

    PagedResponse<AlertRuleResponse> listRules(
        String currentUserId,
        UUID sensorTypeId,
        UUID deviceId,
        String zoneId,
        String farmPlotId,
        Boolean enabled,
        Integer page,
        Integer size,
        String sortBy,
        String sortDir
    );

    AlertRuleResponse getRule(String currentUserId, UUID ruleId);

    AlertRuleResponse updateRule(String currentUserId, UUID ruleId, UpdateAlertRuleRequest request);

    AlertRuleResponse updateRuleEnabled(String currentUserId, UUID ruleId, Boolean enabled);

    void deleteRule(String currentUserId, UUID ruleId);
}
