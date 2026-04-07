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
				.build();
	}
}
