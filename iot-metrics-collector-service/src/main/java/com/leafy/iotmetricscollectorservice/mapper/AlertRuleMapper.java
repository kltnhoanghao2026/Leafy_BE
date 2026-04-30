package com.leafy.iotmetricscollectorservice.mapper;

import com.leafy.iotmetricscollectorservice.dto.alert_rule.AlertRuleResponse;
import com.leafy.iotmetricscollectorservice.model.AlertRule;
import org.springframework.stereotype.Component;

@Component
public class AlertRuleMapper {

    public AlertRuleResponse toAlertRuleResponse(AlertRule alertRule) {
        if (alertRule == null) {
            return null;
        }

        AlertRuleResponse response = new AlertRuleResponse();
        response.setId(alertRule.getId());
        response.setSensorTypeId(alertRule.getSensorType() != null ? alertRule.getSensorType().getId() : null);
        response.setDeviceId(alertRule.getDevice() != null ? alertRule.getDevice().getId() : null);
        response.setZoneId(alertRule.getZone() != null ? alertRule.getZone().getId() : null);
        response.setFarmPlotId(alertRule.getFarmPlot() != null ? alertRule.getFarmPlot().getId() : null);
        response.setOwnerUserId(alertRule.getOwnerUser() != null ? alertRule.getOwnerUser().getId() : null);
        response.setMinThreshold(alertRule.getMinThreshold());
        response.setMaxThreshold(alertRule.getMaxThreshold());
        response.setSeverity(alertRule.getSeverity() != null ? alertRule.getSeverity().name() : null);
        response.setCooldownMinutes(alertRule.getCooldownMinutes());
        response.setNotifyWeb(alertRule.getNotifyWeb());
        response.setNotifyMobile(alertRule.getNotifyMobile());
        response.setEnabled(alertRule.getEnabled());
        response.setCreatedAt(alertRule.getCreatedAt());
        response.setUpdatedAt(alertRule.getUpdatedAt());
        return response;
    }
}
