package com.leafy.searchservice.services.sync;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.client.PlantManagementClient;
import com.leafy.searchservice.client.dto.plan.PlantManagementPlanResponse;
import com.leafy.searchservice.config.ElasticSearchProperties;
import com.leafy.searchservice.model.elasticsearch.PlanIndex;
import com.leafy.searchservice.model.elasticsearch.ProfileIndex;
import com.leafy.searchservice.repository.PlanIndexSearchRepository;
import com.leafy.searchservice.repository.ProfileIndexSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanIndexSyncImpl {

    private final PlantManagementClient plantManagementClient;
    private final PlanIndexSearchRepository planIndexSearchRepository;
    private final ProfileIndexSearchRepository profileIndexSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticSearchProperties elasticSearchProperties;

    // ── Index lifecycle ───────────────────────────────────────────────────────

    public void resetIndex() {
        String planIndexAlias = elasticSearchProperties.getPlanAlias();
        IndexOperations indexOperations = elasticsearchOperations.indexOps(IndexCoordinates.of(planIndexAlias));

        if (indexOperations.exists()) {
            indexOperations.delete();
            log.info("Deleted existing Elasticsearch plan index for alias={}", planIndexAlias);
        }

        var settings = indexOperations.createSettings(PlanIndex.class);
        indexOperations.create(settings);
        indexOperations.putMapping(indexOperations.createMapping(PlanIndex.class));
        log.info("Created fresh empty Elasticsearch plan index and mapping for alias={}", planIndexAlias);
    }

    // ── Reindex all ───────────────────────────────────────────────────────────

    public int reindexAll(int pageSize) {
        resetIndex();

        int indexedCount = 0;
        int page = 0;

        while (true) {
            List<PlantManagementPlanResponse> plans = getPlansBatch(page, pageSize);
            if (plans.isEmpty()) {
                break;
            }

            List<PlanIndex> documents = plans.stream()
                    .map(this::toPlanIndex)
                    .filter(document -> document != null)
                    .toList();

            if (!documents.isEmpty()) {
                planIndexSearchRepository.saveAll(documents);
                indexedCount += documents.size();
            }

            page += 1;
        }

        return indexedCount;
    }

    // ── Real-time upsert ──────────────────────────────────────────────────────

    public void upsertPlan(String planId) {
        PlantManagementPlanResponse plan = getPlanById(planId);
        PlanIndex document = toPlanIndex(plan);

        if (document == null) {
            planIndexSearchRepository.deleteById(planId);
            log.info("Removed plan from index because it returned null: planId={}", planId);
            return;
        }

        planIndexSearchRepository.save(document);
        log.info("Upserted plan index document: planId={}", planId);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void deletePlan(String planId) {
        planIndexSearchRepository.deleteById(planId);
        log.info("Deleted plan index document: planId={}", planId);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private PlanIndex toPlanIndex(PlantManagementPlanResponse plan) {
        if (plan == null) {
            return null;
        }

        // Denormalize creator info from profile index
        String creatorName = null;
        String creatorAvatar = null;
        String creatorRole = null;
        Boolean creatorVerified = null;

        if (plan.getCreatorId() != null) {
            Optional<ProfileIndex> creatorProfile = profileIndexSearchRepository.findById(plan.getCreatorId());
            if (creatorProfile.isPresent()) {
                ProfileIndex profile = creatorProfile.get();
                creatorName = profile.getFullName();
                creatorAvatar = profile.getAvatar() != null ? profile.getAvatar() : profile.getProfilePicture();
                creatorRole = profile.getRole() != null ? profile.getRole().name() : null;
                creatorVerified = profile.getIsVerified();
            }
        }

        return PlanIndex.builder()
                .id(plan.getId())
                .creatorId(plan.getCreatorId())
                .ownerId(plan.getOwnerId())
                .ragPlanId(plan.getRagPlanId())
                .planName(plan.getPlanName())
                .diseaseName(plan.getDiseaseName())
                .question(plan.getQuestion())
                .source(plan.getSource())
                .confidenceScore(plan.getConfidenceScore())
                .severityLevel(plan.getSeverityLevel())
                .urgency(plan.getUrgency())
                .requiredInputs(plan.getRequiredInputs())
                .safetyWarnings(plan.getSafetyWarnings())
                .successIndicators(plan.getSuccessIndicators())
                .estimatedCost(plan.getEstimatedCost())
                .isPublic(plan.getIsPublic())
                .sourceType(plan.getSourceType())
                .eventCount(plan.getEvents() != null ? plan.getEvents().size() : 0)
                .applyCount(plan.getApplyCount())
                .successApplyCount(plan.getSuccessApplyCount())
                .failedApplyCount(plan.getFailedApplyCount())
                .creatorName(creatorName)
                .creatorAvatar(creatorAvatar)
                .creatorRole(creatorRole)
                .creatorVerified(creatorVerified)
                .createdAt(truncateToSecond(plan.getCreatedAt()))
                .build();
    }

    // ── Client helpers ────────────────────────────────────────────────────────

    private PlantManagementPlanResponse getPlanById(String planId) {
        ApiResponse<PlantManagementPlanResponse> response = plantManagementClient.getPlanById(planId);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("Plant management service returned empty plan data for planId=" + planId);
        }
        return response.data();
    }

    private List<PlantManagementPlanResponse> getPlansBatch(int page, int pageSize) {
        ApiResponse<List<PlantManagementPlanResponse>> response = plantManagementClient.getPlansBatch(page, pageSize);
        if (response == null || response.data() == null) {
            return Collections.emptyList();
        }
        return response.data();
    }

    private LocalDateTime truncateToSecond(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.truncatedTo(ChronoUnit.SECONDS);
    }
}
