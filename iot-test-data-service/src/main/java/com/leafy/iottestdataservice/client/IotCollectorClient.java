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

    CollectorDeviceResponse claimDevice(String currentUserId, CollectorClaimDeviceRequest request);

    CollectorDeviceConfigResponse getDeviceConfig(UUID deviceId);

    CollectorDeviceConfigResponse updateDeviceConfig(UUID deviceId, CollectorDeviceConfigRequest request);

    CollectorDeviceConfigResponse pushDeviceConfig(UUID deviceId);

    CollectorAlertRuleResponse createAlertRule(String currentUserId, CollectorAlertRuleRequest request);

    CollectorPagedResponse<CollectorAlertRuleResponse> getAlertRules(
        String currentUserId,
        UUID sensorTypeId,
        UUID deviceId,
        String zoneId,
        String farmPlotId,
        Boolean enabled,
        int page,
        int size,
        String sortBy,
        String sortDir
    );

    CollectorPagedResponse<CollectorDeviceResponse> getMyDevices(
        String currentUserId,
        int page,
        int size,
        String sortBy,
        String sortDir,
        String status,
        String provisioningStatus,
        String zoneId,
        String farmPlotId,
        String keyword
    );
}
