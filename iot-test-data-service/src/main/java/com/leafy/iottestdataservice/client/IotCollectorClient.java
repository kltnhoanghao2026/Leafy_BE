package com.leafy.iottestdataservice.client;

import com.leafy.iottestdataservice.client.dto.CollectorAlertRuleRequest;
import com.leafy.iottestdataservice.client.dto.CollectorAlertRuleResponse;
import com.leafy.iottestdataservice.client.dto.CollectorClaimDeviceRequest;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceConfigRequest;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceConfigResponse;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.client.dto.CollectorGenerateClaimCodeResponse;
import com.leafy.iottestdataservice.client.dto.CollectorPagedResponse;
import com.leafy.iottestdataservice.client.dto.CollectorProvisionDeviceRequest;
import java.util.UUID;

public interface IotCollectorClient {

    CollectorDeviceResponse provisionDevice(CollectorProvisionDeviceRequest request);

    CollectorGenerateClaimCodeResponse generateClaimCode(UUID deviceId);

    CollectorDeviceResponse claimDevice(UUID currentUserId, CollectorClaimDeviceRequest request);

    CollectorDeviceConfigResponse getDeviceConfig(UUID deviceId);

    CollectorDeviceConfigResponse updateDeviceConfig(UUID deviceId, CollectorDeviceConfigRequest request);

    CollectorDeviceConfigResponse pushDeviceConfig(UUID deviceId);

    CollectorAlertRuleResponse createAlertRule(UUID currentUserId, CollectorAlertRuleRequest request);

    CollectorPagedResponse<CollectorAlertRuleResponse> getAlertRules(
        UUID currentUserId,
        UUID sensorTypeId,
        UUID deviceId,
        UUID zoneId,
        UUID farmPlotId,
        Boolean enabled,
        int page,
        int size,
        String sortBy,
        String sortDir
    );

    CollectorPagedResponse<CollectorDeviceResponse> getMyDevices(
        UUID currentUserId,
        int page,
        int size,
        String sortBy,
        String sortDir,
        String status,
        String provisioningStatus,
        UUID zoneId,
        UUID farmPlotId,
        String keyword
    );
}
