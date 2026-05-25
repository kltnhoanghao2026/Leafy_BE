package com.leafy.iotmetricscollectorservice.mapper;

import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventItemResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.DeviceConfigSnapshotResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.LatestReadingItemResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.DeviceMediaSummaryResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.SensorChartPointResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.DeviceDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceConfigResponse;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceResponse;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.aggregate.BaseSensorReadingAgg;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorLatestReading;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DashboardQueryMapper {

    public LatestReadingItemResponse toLatestReadingItemResponse(SensorLatestReading latestReading) {
        if (latestReading == null) {
            return null;
        }

        LatestReadingItemResponse response = new LatestReadingItemResponse();
        response.setSensorTypeId(
            latestReading.getSensorType() != null ? latestReading.getSensorType().getId() : null
        );
        response.setSensorCode(
            latestReading.getSensorType() != null ? latestReading.getSensorType().getCode() : null
        );
        response.setSensorName(
            latestReading.getSensorType() != null ? latestReading.getSensorType().getName() : null
        );
        response.setUnit(
            latestReading.getSensorType() != null ? latestReading.getSensorType().getUnit() : null
        );
        response.setValue(latestReading.getReadingValue());
        response.setReadingTime(latestReading.getReadingTime());
        response.setQualityStatus(
            latestReading.getQualityStatus() != null ? latestReading.getQualityStatus().name() : null
        );
        return response;
    }

    public List<LatestReadingItemResponse> toLatestReadingItemResponses(List<SensorLatestReading> latestReadings) {
        if (latestReadings == null || latestReadings.isEmpty()) {
            return Collections.emptyList();
        }

        return latestReadings.stream()
            .map(this::toLatestReadingItemResponse)
            .toList();
    }

    public SensorChartPointResponse toSensorChartPointResponse(BaseSensorReadingAgg aggregateReading) {
        if (aggregateReading == null) {
            return null;
        }

        SensorChartPointResponse response = new SensorChartPointResponse();
        response.setBucketStart(aggregateReading.getBucketStart());
        response.setBucketEnd(aggregateReading.getBucketEnd());
        response.setAvgValue(aggregateReading.getAvgValue());
        response.setMinValue(aggregateReading.getMinValue());
        response.setMaxValue(aggregateReading.getMaxValue());
        response.setSampleCount(aggregateReading.getSampleCount());
        return response;
    }

    public List<SensorChartPointResponse> toSensorChartPointResponses(List<? extends BaseSensorReadingAgg> aggregateReadings) {
        if (aggregateReadings == null || aggregateReadings.isEmpty()) {
            return Collections.emptyList();
        }

        return aggregateReadings.stream()
            .map(this::toSensorChartPointResponse)
            .toList();
    }

    public DeviceDetailResponse toDeviceDetailResponse(IoTDevice device) {
        if (device == null) {
            return null;
        }

        DeviceDetailResponse response = new DeviceDetailResponse();
        response.setDeviceId(device.getId());
        response.setDeviceUid(device.getDeviceUid());
        response.setDeviceCode(device.getDeviceCode());
        response.setDeviceName(device.getDeviceName());
        response.setDeviceType(device.getDeviceType());
        response.setFirmwareVersion(device.getFirmwareVersion());
        response.setStatus(device.getStatus() != null ? device.getStatus().name() : null);
        response.setProvisioningStatus(
            device.getProvisioningStatus() != null ? device.getProvisioningStatus().name() : null
        );
        response.setIsActive(device.getIsActive());
        response.setOwnerUserId(device.getOwnerUser() != null ? device.getOwnerUser().getId() : null);
        response.setFarmPlotId(device.getFarmPlot() != null ? device.getFarmPlot().getId() : null);
        response.setZoneId(device.getZone() != null ? device.getZone().getId() : null);
        response.setLastSeenAt(device.getLastSeenAt());
        return response;
    }

    public DeviceResponse toDeviceResponse(IoTDevice device) {
        if (device == null) {
            return null;
        }

        DeviceResponse response = new DeviceResponse();
        response.setId(device.getId());
        response.setDeviceUid(device.getDeviceUid());
        response.setDeviceCode(device.getDeviceCode());
        response.setDeviceName(device.getDeviceName());
        response.setDeviceType(device.getDeviceType());
        response.setFirmwareVersion(device.getFirmwareVersion());
        response.setIsActive(device.getIsActive());
        response.setStatus(device.getStatus() != null ? device.getStatus().name() : null);
        response.setProvisioningStatus(
            device.getProvisioningStatus() != null ? device.getProvisioningStatus().name() : null
        );
        response.setOwnerUserId(device.getOwnerUser() != null ? device.getOwnerUser().getId() : null);
        response.setFarmPlotId(device.getFarmPlot() != null ? device.getFarmPlot().getId() : null);
        response.setZoneId(device.getZone() != null ? device.getZone().getId() : null);
        response.setLastSeenAt(device.getLastSeenAt());
        return response;
    }

    public DeviceMediaSummaryResponse toDeviceMediaSummaryResponse(DeviceMediaEvent mediaEvent) {
        if (mediaEvent == null) {
            return null;
        }

        DeviceMediaSummaryResponse response = new DeviceMediaSummaryResponse();
        response.setMediaEventId(mediaEvent.getId());
        response.setFileId(mediaEvent.getFile() != null ? mediaEvent.getFile().getId() : null);
        response.setMediaType(mediaEvent.getMediaType());
        response.setTriggerType(mediaEvent.getTriggerType());
        response.setCapturedAt(mediaEvent.getCapturedAt());
        response.setDeviceId(mediaEvent.getDevice() != null ? mediaEvent.getDevice().getId() : null);
        response.setZoneId(mediaEvent.getZone() != null ? mediaEvent.getZone().getId() : null);
        return response;
    }

    public DeviceConfigSnapshotResponse toDeviceConfigSnapshotResponse(DeviceConfig deviceConfig) {
        if (deviceConfig == null) {
            return null;
        }

        DeviceConfigSnapshotResponse response = new DeviceConfigSnapshotResponse();
        response.setConfigVersion(deviceConfig.getConfigVersion());
        response.setSamplingIntervalSec(deviceConfig.getSamplingIntervalSec());
        response.setPublishIntervalSec(deviceConfig.getPublishIntervalSec());
        response.setOfflineTimeoutSec(deviceConfig.getOfflineTimeoutSec());
        response.setAlertEnabled(deviceConfig.getAlertEnabled());
        response.setAppliedAt(deviceConfig.getAppliedAt());
        return response;
    }

    public DeviceConfigResponse toDeviceConfigResponse(DeviceConfig deviceConfig) {
        if (deviceConfig == null) {
            return null;
        }

        DeviceConfigResponse response = new DeviceConfigResponse();
        response.setDeviceId(deviceConfig.getDevice() != null ? deviceConfig.getDevice().getId() : null);
        response.setConfigVersion(deviceConfig.getConfigVersion());
        response.setSamplingIntervalSec(deviceConfig.getSamplingIntervalSec());
        response.setPublishIntervalSec(deviceConfig.getPublishIntervalSec());
        response.setOfflineTimeoutSec(deviceConfig.getOfflineTimeoutSec());
        response.setAlertEnabled(deviceConfig.getAlertEnabled());
        response.setAppliedAt(deviceConfig.getAppliedAt());
        response.setLastPushStatus(deviceConfig.getLastPushStatus() != null ? deviceConfig.getLastPushStatus().name() : null);
        response.setLastAckAt(deviceConfig.getLastAckAt());
        response.setLastPushError(deviceConfig.getLastPushError());
        return response;
    }

    public AlertEventItemResponse toAlertEventItemResponse(AlertEvent alertEvent) {
        if (alertEvent == null) {
            return null;
        }

        AlertEventItemResponse response = new AlertEventItemResponse();
        response.setId(alertEvent.getId());
        response.setDeviceId(alertEvent.getDevice() != null ? alertEvent.getDevice().getId() : null);
        response.setDeviceName(alertEvent.getDevice() != null ? alertEvent.getDevice().getDeviceName() : null);
        response.setDeviceCode(alertEvent.getDevice() != null ? alertEvent.getDevice().getDeviceCode() : null);
        response.setZoneId(resolveZoneId(alertEvent));
        response.setFarmPlotId(
            alertEvent.getDevice() != null && alertEvent.getDevice().getFarmPlot() != null
                ? alertEvent.getDevice().getFarmPlot().getId()
                : null
        );
        response.setSensorTypeId(alertEvent.getSensorType() != null ? alertEvent.getSensorType().getId() : null);
        response.setSensorCode(alertEvent.getSensorType() != null ? alertEvent.getSensorType().getCode() : null);
        response.setSensorName(alertEvent.getSensorType() != null ? alertEvent.getSensorType().getName() : null);
        response.setUnit(alertEvent.getSensorType() != null ? alertEvent.getSensorType().getUnit() : null);
        response.setAlertRuleId(alertEvent.getAlertRule() != null ? alertEvent.getAlertRule().getId() : null);
        response.setAlertType(alertEvent.getAlertType());
        response.setMessage(alertEvent.getMessage());
        response.setSeverity(alertEvent.getSeverity() != null ? alertEvent.getSeverity().name() : null);
        response.setStatus(alertEvent.getStatus() != null ? alertEvent.getStatus().name() : null);
        response.setTriggerValue(alertEvent.getTriggerValue());
        response.setThresholdMin(alertEvent.getThresholdMin());
        response.setThresholdMax(alertEvent.getThresholdMax());
        response.setOpenedAt(alertEvent.getOpenedAt());
        response.setAcknowledgedAt(alertEvent.getAcknowledgedAt());
        response.setResolvedAt(alertEvent.getResolvedAt());
        response.setPushSent(alertEvent.getPushSent());
        return response;
    }

    private String resolveZoneId(AlertEvent alertEvent) {
        if (alertEvent.getZone() != null) {
            return alertEvent.getZone().getId();
        }

        return alertEvent.getDevice() != null && alertEvent.getDevice().getZone() != null
            ? alertEvent.getDevice().getZone().getId()
            : null;
    }

    public AlertEventDetailResponse toAlertEventDetailResponse(AlertEvent alertEvent) {
        if (alertEvent == null) {
            return null;
        }

        AlertEventItemResponse itemResponse = toAlertEventItemResponse(alertEvent);
        AlertEventDetailResponse response = new AlertEventDetailResponse();
        response.setId(itemResponse.getId());
        response.setDeviceId(itemResponse.getDeviceId());
        response.setDeviceName(itemResponse.getDeviceName());
        response.setDeviceCode(itemResponse.getDeviceCode());
        response.setZoneId(itemResponse.getZoneId());
        response.setFarmPlotId(itemResponse.getFarmPlotId());
        response.setSensorTypeId(itemResponse.getSensorTypeId());
        response.setSensorCode(itemResponse.getSensorCode());
        response.setSensorName(itemResponse.getSensorName());
        response.setUnit(itemResponse.getUnit());
        response.setAlertRuleId(itemResponse.getAlertRuleId());
        response.setAlertType(itemResponse.getAlertType());
        response.setMessage(itemResponse.getMessage());
        response.setSeverity(itemResponse.getSeverity());
        response.setStatus(itemResponse.getStatus());
        response.setTriggerValue(itemResponse.getTriggerValue());
        response.setThresholdMin(itemResponse.getThresholdMin());
        response.setThresholdMax(itemResponse.getThresholdMax());
        response.setOpenedAt(itemResponse.getOpenedAt());
        response.setAcknowledgedAt(itemResponse.getAcknowledgedAt());
        response.setResolvedAt(itemResponse.getResolvedAt());
        response.setPushSent(itemResponse.getPushSent());
        return response;
    }
}
