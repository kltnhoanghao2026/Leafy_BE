package com.leafy.plantmanagementservice.client;

import com.leafy.plantmanagementservice.client.dto.ExternalApiResponse;
import com.leafy.plantmanagementservice.client.dto.PagedResponse;
import com.leafy.plantmanagementservice.client.dto.ProfileSummary;
import com.leafy.plantmanagementservice.model.enums.ConsultingDataType;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for communicating with profile-service.
 *
 * Security headers (X-User-Id, X-User-Email, X-User-Roles, X-Profile-Id) are
 * injected automatically by {@code FeignSecurityInterceptor} from the common
 * module.
 */
@FeignClient(name = "profile-service")
public interface ProfileServiceClient {

    @GetMapping("/profiles/active")
    ExternalApiResponse<PagedResponse<ProfileSummary>> getActiveProfiles(
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam("sortBy") String sortBy,
            @RequestParam("sortDir") String sortDir);

    @GetMapping("/internal/profiles/consulting/validate")
    ExternalApiResponse<Boolean> validateConsulting(
            @RequestParam("expertProfileId") String expertProfileId,
            @RequestParam("farmerProfileId") String farmerProfileId);

    @GetMapping("/internal/profiles/consulting/validate-with-toggle")
    ExternalApiResponse<Boolean> validateConsultingWithToggle(
            @RequestParam("expertProfileId") String expertProfileId,
            @RequestParam("farmerProfileId") String farmerProfileId,
            @RequestParam("dataType") ConsultingDataType dataType);

    @GetMapping("/internal/profiles/consulting/farmers")
    ExternalApiResponse<List<String>> getConsultingFarmerIds(
            @RequestParam("expertProfileId") String expertProfileId);

    @GetMapping("/internal/profiles/{profileId}")
    ExternalApiResponse<ProfileSummary> getProfileById(@PathVariable("profileId") String profileId);

    @GetMapping("/internal/profiles/by-ids")
    ExternalApiResponse<List<ProfileSummary>> getProfilesByIds(@RequestParam("ids") List<String> ids);
}
