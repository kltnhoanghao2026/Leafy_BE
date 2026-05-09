package com.leafy.iottestdataservice.client;

import com.leafy.iottestdataservice.client.dto.ApiResponse;
import com.leafy.iottestdataservice.client.dto.FarmPlotResponse;
import com.leafy.iottestdataservice.client.dto.FarmZoneResponse;
import com.leafy.iottestdataservice.config.SeedProperties;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestFarmSeedClient implements FarmSeedClient {

    private final SeedProperties seedProperties;
    private final RestClient restClient;

    public RestFarmSeedClient(RestClient.Builder builder, SeedProperties seedProperties) {
        this.seedProperties = seedProperties;
        this.restClient = builder.baseUrl(seedProperties.getFarm().getBaseUrl()).build();
    }

    @Override
    public List<FarmPlotResponse> getFarmPlots(String ownerProfileId) {
        ApiResponse<List<FarmPlotResponse>> response = withHeaders(restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/farms/plots")
                .queryParam("ownerProfileId", ownerProfileId)
                .build()))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        return response == null || response.data() == null ? List.of() : response.data();
    }

    @Override
    public List<FarmZoneResponse> getFarmZones(String farmPlotId) {
        ApiResponse<List<FarmZoneResponse>> response = withHeaders(restClient.get()
            .uri("/farms/plots/{farmPlotId}/zones", farmPlotId))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        return response == null || response.data() == null ? List.of() : response.data();
    }

    private RestClient.RequestHeadersSpec<?> withHeaders(RestClient.RequestHeadersSpec<?> spec) {
        return spec
            .header("X-User-Id", seedProperties.getAuthHeaders().getUserId())
            .header("X-User-Email", seedProperties.getAuthHeaders().getEmail())
            .header("X-User-Roles", seedProperties.getAuthHeaders().getRoles());
    }
}
