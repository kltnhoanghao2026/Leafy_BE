package com.leafy.searchservice.services.sync;

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

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileIndexSyncImpl {

	private final ProfileIndexSearchRepository profileIndexSearchRepository;
	private final ElasticsearchOperations elasticsearchOperations;
	private final ElasticSearchProperties elasticSearchProperties;
    private final com.leafy.searchservice.client.ProfileClient profileClient;

    public void resetIndex() {
        String profileIndexAlias = elasticSearchProperties.getProfileAlias();
        IndexOperations indexOperations = elasticsearchOperations.indexOps(IndexCoordinates.of(profileIndexAlias));

        if (indexOperations.exists()) {
            indexOperations.delete();
            log.info("Deleted existing Elasticsearch profile index for alias={}", profileIndexAlias);
        }

        var settings = indexOperations.createSettings(ProfileIndex.class);
        indexOperations.create(settings);
        indexOperations.putMapping(indexOperations.createMapping(ProfileIndex.class));
        log.info("Created fresh empty Elasticsearch profile index and mapping for alias={}", profileIndexAlias);
    }

    public int reindexAll(int pageSize) {
        resetIndex();

        int indexedCount = 0;
        String lastId = null;

        while (true) {
            com.leafy.common.dto.ApiResponse<java.util.List<com.leafy.searchservice.client.dto.profile.UserSyncResponse>> response = 
                profileClient.getUsersBatch(lastId, pageSize);
                
            if (response == null || response.data() == null || response.data().isEmpty()) {
                break;
            }

            List<com.leafy.searchservice.client.dto.profile.UserSyncResponse> profiles = response.data();

            List<ProfileIndex> documents = profiles.stream()
                    .map(this::toProfileIndexFromSync)
                    .toList();

            if (!documents.isEmpty()) {
                profileIndexSearchRepository.saveAll(documents);
                indexedCount += documents.size();
            }

            // Update lastId for next cursor
            lastId = profiles.get(profiles.size() - 1).getId();
        }

        return indexedCount;
    }

    private ProfileIndex toProfileIndexFromSync(com.leafy.searchservice.client.dto.profile.UserSyncResponse response) {
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
		String profileIndexAlias = elasticSearchProperties.getProfileAlias();
		IndexOperations indexOperations = elasticsearchOperations.indexOps(IndexCoordinates.of(profileIndexAlias));

		if (indexOperations.exists()) {
			indexOperations.delete();
			log.info("Deleted existing Elasticsearch index for alias={}", profileIndexAlias);
		}

		indexOperations.create();
		indexOperations.putMapping(indexOperations.createMapping(ProfileIndex.class));
		log.info("Created fresh Elasticsearch index and mapping for alias={}", profileIndexAlias);

		return bulkUpsert(documents);
	}

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
}
