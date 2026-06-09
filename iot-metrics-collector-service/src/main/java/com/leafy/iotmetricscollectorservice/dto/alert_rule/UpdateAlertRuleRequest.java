package com.leafy.iotmetricscollectorservice.dto.alert_rule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

public class UpdateAlertRuleRequest {

    private UUID sensorTypeId;
    private UUID deviceId;
    private String zoneId;
    private String farmPlotId;
    private Double minThreshold;
    private Double maxThreshold;
    private String severity;
    private Integer cooldownMinutes;
    private Boolean notifyWeb;
    private Boolean notifyMobile;
    private Boolean enabled;
    private boolean deviceIdSet;
    private boolean zoneIdSet;
    private boolean farmPlotIdSet;

    public UUID getSensorTypeId() {
        return sensorTypeId;
    }

    public void setSensorTypeId(UUID sensorTypeId) {
        this.sensorTypeId = sensorTypeId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
        this.deviceIdSet = true;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
        this.zoneIdSet = true;
    }

    public String getFarmPlotId() {
        return farmPlotId;
    }

    public void setFarmPlotId(String farmPlotId) {
        this.farmPlotId = farmPlotId;
        this.farmPlotIdSet = true;
    }

    public Double getMinThreshold() {
        return minThreshold;
    }

    public void setMinThreshold(Double minThreshold) {
        this.minThreshold = minThreshold;
    }

    public Double getMaxThreshold() {
        return maxThreshold;
    }

    public void setMaxThreshold(Double maxThreshold) {
        this.maxThreshold = maxThreshold;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Integer getCooldownMinutes() {
        return cooldownMinutes;
    }

    public void setCooldownMinutes(Integer cooldownMinutes) {
        this.cooldownMinutes = cooldownMinutes;
    }

    public Boolean getNotifyWeb() {
        return notifyWeb;
    }

    public void setNotifyWeb(Boolean notifyWeb) {
        this.notifyWeb = notifyWeb;
    }

    public Boolean getNotifyMobile() {
        return notifyMobile;
    }

    public void setNotifyMobile(Boolean notifyMobile) {
        this.notifyMobile = notifyMobile;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @JsonIgnore
    public boolean hasScopeFields() {
        return deviceIdSet || zoneIdSet || farmPlotIdSet;
    }

    @JsonIgnore
    public boolean hasDeviceIdField() {
        return deviceIdSet;
    }

    @JsonIgnore
    public boolean hasZoneIdField() {
        return zoneIdSet;
    }

    @JsonIgnore
    public boolean hasFarmPlotIdField() {
        return farmPlotIdSet;
    }
}
