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
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class RestIotCollectorClient implements IotCollectorClient {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final RestClient restClient;

    @Override
    public CollectorDeviceResponse provisionDevice(CollectorProvisionDeviceRequest request) {
        return restClient.post()
            .uri("/iot/devices/provision")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(CollectorDeviceResponse.class);
    }

    @Override
    public CollectorGenerateClaimCodeResponse generateClaimCode(UUID deviceId) {
        return restClient.post()
            .uri("/iot/devices/{deviceId}/claim-code", deviceId)
            .retrieve()
            .body(CollectorGenerateClaimCodeResponse.class);
    }

    @Override
    public CollectorDeviceResponse claimDevice(UUID currentUserId, CollectorClaimDeviceRequest request) {
        return restClient.post()
            .uri("/iot/devices/claim")
            .header(USER_ID_HEADER, currentUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(CollectorDeviceResponse.class);
    }

    @Override
    public CollectorDeviceConfigResponse getDeviceConfig(UUID deviceId) {
        return restClient.get()
            .uri("/iot/devices/{deviceId}/config", deviceId)
            .retrieve()
            .body(CollectorDeviceConfigResponse.class);
    }

    @Override
    public CollectorDeviceConfigResponse updateDeviceConfig(UUID deviceId, CollectorDeviceConfigRequest request) {
        return restClient.put()
            .uri("/iot/devices/{deviceId}/config", deviceId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(CollectorDeviceConfigResponse.class);
    }

    @Override
    public CollectorDeviceConfigResponse pushDeviceConfig(UUID deviceId) {
        return restClient.post()
            .uri("/iot/devices/{deviceId}/config/push", deviceId)
            .retrieve()
            .body(CollectorDeviceConfigResponse.class);
    }

    @Override
    public CollectorAlertRuleResponse createAlertRule(UUID currentUserId, CollectorAlertRuleRequest request) {
        return restClient.post()
            .uri("/iot/alert-rules")
            .header(USER_ID_HEADER, currentUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(CollectorAlertRuleResponse.class);
    }

    @Override
    public CollectorPagedResponse<CollectorAlertRuleResponse> getAlertRules(
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
    ) {
        return restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/iot/alert-rules")
                .queryParamIfPresent("sensorTypeId", java.util.Optional.ofNullable(sensorTypeId))
                .queryParamIfPresent("deviceId", java.util.Optional.ofNullable(deviceId))
                .queryParamIfPresent("zoneId", java.util.Optional.ofNullable(zoneId))
                .queryParamIfPresent("farmPlotId", java.util.Optional.ofNullable(farmPlotId))
                .queryParamIfPresent("enabled", java.util.Optional.ofNullable(enabled))
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParam("sortBy", sortBy)
                .queryParam("sortDir", sortDir)
                .build())
            .header(USER_ID_HEADER, currentUserId.toString())
            .retrieve()
            .body(new ParameterizedTypeReference<CollectorPagedResponse<CollectorAlertRuleResponse>>() {});
    }

    @Override
    public CollectorPagedResponse<CollectorDeviceResponse> getMyDevices(
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
    ) {
        return restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/iot/devices/me")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParam("sortBy", sortBy)
                .queryParam("sortDir", sortDir)
                .queryParamIfPresent("status", java.util.Optional.ofNullable(status))
                .queryParamIfPresent("provisioningStatus", java.util.Optional.ofNullable(provisioningStatus))
                .queryParamIfPresent("zoneId", java.util.Optional.ofNullable(zoneId))
                .queryParamIfPresent("farmPlotId", java.util.Optional.ofNullable(farmPlotId))
                .queryParamIfPresent("keyword", java.util.Optional.ofNullable(keyword))
                .build())
            .header(USER_ID_HEADER, currentUserId.toString())
            .retrieve()
            .body(new ParameterizedTypeReference<CollectorPagedResponse<CollectorDeviceResponse>>() {});
    }
}
