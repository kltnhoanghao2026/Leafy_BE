package com.leafy.searchservice.services.sync;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.client.AuthUserClient;
import com.leafy.searchservice.client.ProfileClient;
import com.leafy.searchservice.client.dto.AuthUserResponse;
import com.leafy.searchservice.client.dto.ProfileServiceProfileResponse;
import com.leafy.searchservice.client.dto.profile.UserSyncResponse;
import com.leafy.searchservice.config.ElasticSearchProperties;
import com.leafy.searchservice.dto.request.sync.ProfileSyncDocumentRequest;
import com.leafy.searchservice.model.elasticsearch.ProfileIndex;
import com.leafy.searchservice.repository.ProfileIndexSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileIndexSyncImpl {

    private final ProfileIndexSearchRepository profileIndexSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticSearchProperties elasticSearchProperties;
    private final ProfileClient profileClient;
    private final AuthUserClient authUserClient;

    // ── Index lifecycle ───────────────────────────────────────────────────────

    public void resetIndex() {
        String alias = elasticSearchProperties.getProfileAlias();
        IndexOperations indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(alias));

        if (indexOps.exists()) {
            indexOps.delete();
            log.info("Deleted existing Elasticsearch profile index for alias={}", alias);
        }

        var settings = indexOps.createSettings(ProfileIndex.class);
        indexOps.create(settings);
        indexOps.putMapping(indexOps.createMapping(ProfileIndex.class));
        log.info("Created fresh empty Elasticsearch profile index and mapping for alias={}", alias);
    }

    // ── Reindex: pull all profiles from profile-service (cursor-based) ────────

    public int reindexAll(int pageSize) {
        resetIndex();

        int indexedCount = 0;
        String lastId = null;

        while (true) {
            List<UserSyncResponse> profiles = getUsersBatch(lastId, pageSize);
            if (profiles.isEmpty()) {
                break;
            }

            List<ProfileIndex> documents = profiles.stream()
                    .map(this::toProfileIndexFromSync)
                    .toList();

            profileIndexSearchRepository.saveAll(documents);
            indexedCount += documents.size();

            lastId = profiles.get(profiles.size() - 1).getId();
        }

        return indexedCount;
    }

    // ── Upsert: real-time event-driven sync ───────────────────────────────────

    /**
     * Fetch the latest data for a single profile from profile-service + auth-service,
     * then save (or update) the corresponding ProfileIndex document.
     * Called by {@link com.leafy.searchservice.listener.ProfileIndexEventListener}.
     */
    public void upsertProfile(String profileId) {
        ProfileServiceProfileResponse profile = fetchProfile(profileId);
        AuthUserResponse user = fetchUser(profile.getUserId());

        ProfileIndex document = toProfileIndex(profile, user);
        profileIndexSearchRepository.save(document);
        log.info("Upserted profile index document: profileId={}", profileId);
    }

    // ── Bulk upsert: push sync (external caller supplies data) ────────────────

    public int bulkUpsert(List<ProfileSyncDocumentRequest> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }

        List<ProfileIndex> profileIndices = documents.stream()
                .map(this::toProfileIndex)
                .toList();

        profileIndexSearchRepository.saveAll(profileIndices);
        return profileIndices.size();
    }

    public int resetAndReindex(List<ProfileSyncDocumentRequest> documents) {
        resetIndex();
        return bulkUpsert(documents);
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    /**
     * Map from the Feign-fetched profile + auth-user pair → {@link ProfileIndex}.
     * Used by {@link #upsertProfile} and {@link #reindexAll} (indirectly via
     * {@link #toProfileIndexFromSync}).
     */
    private ProfileIndex toProfileIndex(ProfileServiceProfileResponse profile, AuthUserResponse user) {
        return ProfileIndex.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .fullName(profile.getFullName())
                .profilePicture(profile.getProfilePicture())
                .avatar(profile.getAvatar())
                .phoneNumber(user != null ? user.getPhoneNumber() : null)
                .email(user != null ? user.getEmail() : null)
                .role(profile.getRole())
                .specialty(profile.getSpecialty())
                .isVerified(profile.getIsVerified())
                .active(profile.getActive())
                .bio(profile.getBio())
                .addressLine(profile.getAddressLine())
                .provinceCode(profile.getProvinceCode())
                .districtCode(profile.getDistrictCode())
                .wardCode(profile.getWardCode())
                .latitude(profile.getLatitude())
                .longitude(profile.getLongitude())
                .build();
    }

    /**
     * Map from the lightweight {@link UserSyncResponse} returned by the
     * profile-service batch endpoint → {@link ProfileIndex}.
     * Used only by {@link #reindexAll}. Auth-service fields (phone/email) are
     * not available in the batch response and default to {@code null}.
     */
    private ProfileIndex toProfileIndexFromSync(UserSyncResponse response) {
        return ProfileIndex.builder()
                .id(response.getId())
                .userId(response.getUserId())
                .fullName(response.getFullName())
                .profilePicture(response.getProfilePicture())
                .avatar(response.getAvatar())
                .role(response.getRole())
                .specialty(response.getSpecialty())
                .isVerified(response.getIsVerified())
                .active(response.isActive())
                .bio(response.getBio())
                .addressLine(response.getAddressLine())
                .provinceCode(response.getProvinceCode())
                .districtCode(response.getDistrictCode())
                .wardCode(response.getWardCode())
                .latitude(response.getLatitude())
                .longitude(response.getLongitude())
                .build();
    }

    /**
     * Map from a push-sync {@link ProfileSyncDocumentRequest} → {@link ProfileIndex}.
     * Used only by {@link #bulkUpsert} / {@link #resetAndReindex}.
     */
    private ProfileIndex toProfileIndex(ProfileSyncDocumentRequest request) {
        return ProfileIndex.builder()
                .id(request.getId())
                .userId(request.getUserId())
                .fullName(request.getFullName())
                .profilePicture(request.getProfilePicture())
                .avatar(request.getAvatar())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .role(request.getRole())
                .specialty(request.getSpecialty())
                .isVerified(request.getIsVerified())
                .active(request.getActive())
                .bio(request.getBio())
                .addressLine(request.getAddressLine())
                .provinceCode(request.getProvinceCode())
                .districtCode(request.getDistrictCode())
                .wardCode(request.getWardCode())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();
    }

    // ── Client helpers ────────────────────────────────────────────────────────

    private List<UserSyncResponse> getUsersBatch(String lastId, int pageSize) {
        ApiResponse<List<UserSyncResponse>> response = profileClient.getUsersBatch(lastId, pageSize);
        if (response == null || response.data() == null) {
            return Collections.emptyList();
        }
        return response.data();
    }

    private ProfileServiceProfileResponse fetchProfile(String profileId) {
        var response = profileClient.getProfileById(profileId);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("Profile service returned empty data for profileId=" + profileId);
        }
        return response.data();
    }

    private AuthUserResponse fetchUser(String userId) {
        var response = authUserClient.getUserById(userId);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("Auth service returned empty data for userId=" + userId);
        }
        return response.data();
    }
}
