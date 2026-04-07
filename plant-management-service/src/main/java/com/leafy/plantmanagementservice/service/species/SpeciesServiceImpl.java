package com.leafy.plantmanagementservice.service.species;

import com.fasterxml.jackson.databind.JsonNode;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.plantmanagementservice.config.PerenualApiProperties;
import com.leafy.plantmanagementservice.dto.request.species.SpeciesCreateRequest;
import com.leafy.plantmanagementservice.dto.request.species.SpeciesUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.species.SpeciesSeedResponse;
import com.leafy.plantmanagementservice.dto.response.species.SpeciesResponse;
import com.leafy.plantmanagementservice.mapper.SpeciesMapper;
import com.leafy.plantmanagementservice.model.Species;
import com.leafy.plantmanagementservice.repository.SpeciesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpeciesServiceImpl implements SpeciesService {

    private final SpeciesRepository speciesRepository;
    private final SpeciesMapper speciesMapper;
    private final RestClient.Builder restClientBuilder;
    private final PerenualApiProperties perenualApiProperties;

    @Override
    @Transactional
    public SpeciesResponse createSpecies(SpeciesCreateRequest request) {
        log.info("Creating new species: {}", request.getCommonName());
        Species species = speciesMapper.toEntity(request);
        Species savedSpecies = speciesRepository.save(species);
        return speciesMapper.toResponse(savedSpecies);
    }

    @Override
    @Transactional
    public SpeciesResponse updateSpecies(String speciesId, SpeciesUpdateRequest request) {
        log.info("Updating species: {}", speciesId);
        Species species = getSpeciesEntityById(speciesId);
        speciesMapper.updateEntityFromRequest(request, species);
        Species updatedSpecies = speciesRepository.save(species);
        return speciesMapper.toResponse(updatedSpecies);
    }

    @Override
    public SpeciesResponse getSpeciesById(String speciesId) {
        log.info("Fetching species by id: {}", speciesId);
        Species species = getSpeciesEntityById(speciesId);
        return speciesMapper.toResponse(species);
    }

    @Override
    public Species getSpeciesEntityById(String speciesId) {
        return speciesRepository.findById(speciesId)
                .orElseThrow(() -> new AppException(ErrorCode.SPECIES_NOT_FOUND));
    }

    @Override
    public Page<SpeciesResponse> getAllSpecies(Pageable pageable) {
        log.info("Fetching all species with pagination");
        return speciesRepository.findAll(pageable)
                .map(speciesMapper::toResponse);
    }

    @Override
    @Transactional
    public SpeciesSeedResponse seedSpeciesFromPerenual(int startPage, int pages, int perPage) {
        validateSeedParams(startPage, pages, perPage);

        String apiKey = normalizeText(perenualApiProperties.getKey());
        if (!StringUtils.hasText(apiKey)) {
            log.error("Perenual API key is missing. Configure perenual.api.key or PERENUAL_API_KEY");
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }

        RestClient restClient = restClientBuilder.baseUrl(perenualApiProperties.getBaseUrl()).build();
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        List<Integer> failedPages = new ArrayList<>();

        for (int currentPage = startPage; currentPage < startPage + pages; currentPage++) {
            JsonNode pageData = fetchPerenualPageData(restClient, apiKey, currentPage, perPage, failedPages);
            if (pageData == null) {
                continue;
            }

            for (JsonNode perenualSpecies : pageData) {
                String commonName = extractCommonName(perenualSpecies);
                if (!StringUtils.hasText(commonName)) {
                    skippedCount++;
                    continue;
                }

                if (!isCoffeeRelated(perenualSpecies, commonName)) {
                    skippedCount++;
                    continue;
                }

                Species species = speciesRepository.findByCommonNameIgnoreCase(commonName)
                        .orElseGet(Species::new);
                boolean isNewSpecies = species.getId() == null;

                applyPerenualData(species, perenualSpecies, commonName);
                if (species.getCommonDiseaseIds() == null) {
                    species.setCommonDiseaseIds(new ArrayList<>());
                }
                speciesRepository.save(species);

                if (isNewSpecies) {
                    createdCount++;
                } else {
                    updatedCount++;
                }
            }
        }

        return SpeciesSeedResponse.builder()
                .startPage(startPage)
                .pagesRequested(pages)
                .perPage(perPage)
                .totalSaved(createdCount + updatedCount)
                .createdCount(createdCount)
                .updatedCount(updatedCount)
                .skippedCount(skippedCount)
                .failedPages(failedPages)
                .build();
    }

    @Override
    @Transactional
    public void deleteSpecies(String speciesId) {
        log.info("Deleting species: {}", speciesId);
        if (!speciesRepository.existsById(speciesId)) {
            throw new AppException(ErrorCode.SPECIES_NOT_FOUND);
        }
        speciesRepository.deleteById(speciesId);
    }

    private JsonNode fetchPerenualPageData(RestClient restClient,
                                           String apiKey,
                                           int page,
                                           int perPage,
                                           List<Integer> failedPages) {
        try {
            JsonNode responseNode = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/species-list")
                            .queryParam("key", apiKey)
                            .queryParam("page", page)
                            .queryParam("per_page", perPage)
                            .queryParam("q", "coffee")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            if (responseNode == null || !responseNode.path("data").isArray()) {
                log.warn("Perenual returned invalid payload for page {}", page);
                failedPages.add(page);
                return null;
            }

            return responseNode.path("data");
        } catch (RestClientException ex) {
            log.error("Failed to fetch Perenual page {}", page, ex);
            failedPages.add(page);
            return null;
        }
    }

    private void applyPerenualData(Species species, JsonNode perenualSpecies, String commonName) {
        species.setCommonName(commonName);

        String cultivarName = joinTextNodeValues(perenualSpecies.path("scientific_name"));
        if (StringUtils.hasText(cultivarName)) {
            species.setCultivarName(cultivarName);
        }

        Integer waterFrequencyDays = mapWateringToDays(perenualSpecies.path("watering").asText(null));
        if (waterFrequencyDays != null) {
            species.setWaterFrequencyDays(waterFrequencyDays);
        }

        String lightRequirements = joinTextNodeValues(perenualSpecies.path("sunlight"));
        if (StringUtils.hasText(lightRequirements)) {
            species.setLightRequirements(lightRequirements);
        }

        String cycle = normalizeText(perenualSpecies.path("cycle").asText(null));
        if (StringUtils.hasText(cycle)) {
            species.setPlantingSeason(cycle);
        }

        species.setIdealEnv(mergeIdealEnv(species.getIdealEnv(), perenualSpecies));
    }

    private Map<String, Object> mergeIdealEnv(Map<String, Object> existingIdealEnv, JsonNode perenualSpecies) {
        Map<String, Object> idealEnv = existingIdealEnv != null
                ? new HashMap<>(existingIdealEnv)
                : new HashMap<>();

        idealEnv.put("source", "perenual");

        JsonNode perenualId = perenualSpecies.path("id");
        if (perenualId.isNumber()) {
            idealEnv.put("perenualId", perenualId.asLong());
        }

        String watering = normalizeText(perenualSpecies.path("watering").asText(null));
        if (StringUtils.hasText(watering)) {
            idealEnv.put("watering", watering);
        }

        String cycle = normalizeText(perenualSpecies.path("cycle").asText(null));
        if (StringUtils.hasText(cycle)) {
            idealEnv.put("cycle", cycle);
        }

        List<String> sunlightValues = extractTextValues(perenualSpecies.path("sunlight"));
        if (!sunlightValues.isEmpty()) {
            idealEnv.put("sunlight", sunlightValues);
        }

        List<String> aliases = extractTextValues(perenualSpecies.path("other_name"));
        if (!aliases.isEmpty()) {
            idealEnv.put("aliases", aliases);
        }

        String imageUrl = extractImageUrl(perenualSpecies.path("default_image"));
        if (StringUtils.hasText(imageUrl)) {
            idealEnv.put("imageUrl", imageUrl);
        }

        return idealEnv;
    }

    private boolean isCoffeeRelated(JsonNode perenualSpecies, String commonName) {
        String lowerCommon = commonName.toLowerCase(Locale.ROOT);
        if (lowerCommon.contains("coffee") || lowerCommon.contains("coffea")) {
            return true;
        }

        List<String> scientificNames = extractTextValues(perenualSpecies.path("scientific_name"));
        for (String name : scientificNames) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("coffea") || lower.contains("coffee")) {
                return true;
            }
        }

        List<String> otherNames = extractTextValues(perenualSpecies.path("other_name"));
        for (String name : otherNames) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("coffee") || lower.contains("coffea")) {
                return true;
            }
        }

        return false;
    }

    private String extractCommonName(JsonNode perenualSpecies) {
        String commonName = normalizeText(perenualSpecies.path("common_name").asText(null));
        if (StringUtils.hasText(commonName)) {
            return commonName;
        }

        List<String> scientificNames = extractTextValues(perenualSpecies.path("scientific_name"));
        return scientificNames.isEmpty() ? null : scientificNames.get(0);
    }

    private String joinTextNodeValues(JsonNode node) {
        List<String> values = extractTextValues(node);
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private List<String> extractTextValues(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return List.of();
        }

        Set<String> values = new LinkedHashSet<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = normalizeText(item.asText(null));
                if (StringUtils.hasText(value)) {
                    values.add(value);
                }
            }
        } else {
            String value = normalizeText(node.asText(null));
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }

        return new ArrayList<>(values);
    }

    private String extractImageUrl(JsonNode defaultImageNode) {
        if (defaultImageNode == null || defaultImageNode.isNull() || !defaultImageNode.isObject()) {
            return null;
        }

        String[] preferredFields = { "original_url", "regular_url", "medium_url", "small_url", "thumbnail" };
        for (String field : preferredFields) {
            String value = normalizeText(defaultImageNode.path(field).asText(null));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Integer mapWateringToDays(String watering) {
        if (!StringUtils.hasText(watering)) {
            return null;
        }

        String normalizedWatering = watering.toLowerCase(Locale.ROOT);
        if (normalizedWatering.contains("frequent")) {
            return 2;
        }
        if (normalizedWatering.contains("average") || normalizedWatering.contains("moderate")) {
            return 4;
        }
        if (normalizedWatering.contains("minimum") || normalizedWatering.contains("rare")
                || normalizedWatering.contains("low")) {
            return 7;
        }
        return null;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateSeedParams(int startPage, int pages, int perPage) {
        if (startPage < 1 || pages < 1 || perPage < 1) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
