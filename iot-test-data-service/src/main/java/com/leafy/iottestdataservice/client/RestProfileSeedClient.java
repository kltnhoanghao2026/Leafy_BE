package com.leafy.iottestdataservice.client;

import com.leafy.iottestdataservice.client.dto.ApiResponse;
import com.leafy.iottestdataservice.client.dto.PageResponse;
import com.leafy.iottestdataservice.client.dto.ProfileResponse;
import com.leafy.iottestdataservice.config.SeedProperties;
import java.util.List;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestProfileSeedClient implements ProfileSeedClient {

    private final SeedProperties seedProperties;
    private final RestClient restClient;

    public RestProfileSeedClient(RestClient.Builder builder, SeedProperties seedProperties) {
        this.seedProperties = seedProperties;
        this.restClient = builder.baseUrl(seedProperties.getProfile().getBaseUrl()).build();
    }

    @Override
    public List<ProfileResponse> getActiveProfiles(int page, int size) {
        ApiResponse<PageResponse<ProfileResponse>> response = withHeaders(restClient.get()
            .uri(uriBuilder -> uriBuilder.path("/profiles/active")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParam("sortBy", "createdAt")
                .queryParam("sortDir", "desc")
                .build()))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        return response == null || response.data() == null || response.data().content() == null
            ? List.of()
            : response.data().content();
    }

    @Override
    public Optional<ProfileResponse> getProfileById(String profileId) {
        ApiResponse<ProfileResponse> response = withHeaders(restClient.get()
            .uri("/profiles/{profileId}", profileId))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        return Optional.ofNullable(response).map(ApiResponse::data);
    }

    @Override
    public Optional<ProfileResponse> getProfileByUserId(String userId) {
        ApiResponse<ProfileResponse> response = withHeaders(restClient.get()
            .uri("/profiles/user/{userId}", userId))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        return Optional.ofNullable(response).map(ApiResponse::data);
    }

    private RestClient.RequestHeadersSpec<?> withHeaders(RestClient.RequestHeadersSpec<?> spec) {
        return spec
            .header("X-User-Id", seedProperties.getAuthHeaders().getUserId())
            .header("X-User-Email", seedProperties.getAuthHeaders().getEmail())
            .header("X-User-Roles", seedProperties.getAuthHeaders().getRoles());
    }
}
