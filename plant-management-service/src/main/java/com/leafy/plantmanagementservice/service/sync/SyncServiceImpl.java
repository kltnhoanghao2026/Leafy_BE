package com.leafy.plantmanagementservice.service.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventUpdateRequest;
import com.leafy.plantmanagementservice.dto.request.plant.PlantCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plant.PlantUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.plantmanagementservice.dto.response.farmzone.FarmZoneResponse;
import com.leafy.plantmanagementservice.dto.response.plant.PlantResponse;
import com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse;
import com.leafy.plantmanagementservice.dto.sync.*;
import com.leafy.plantmanagementservice.model.FarmPlot;
import com.leafy.plantmanagementservice.model.FarmZone;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.repository.FarmPlotRepository;
import com.leafy.plantmanagementservice.repository.FarmZoneRepository;
import com.leafy.plantmanagementservice.repository.PlantEventRepository;
import com.leafy.plantmanagementservice.repository.PlantRepository;
import com.leafy.plantmanagementservice.service.farmplot.FarmPlotService;
import com.leafy.plantmanagementservice.service.farmzone.FarmZoneService;
import com.leafy.plantmanagementservice.service.plant.PlantService;
import com.leafy.plantmanagementservice.service.plantevent.PlantEventService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SyncServiceImpl implements SyncService {

    ObjectMapper objectMapper;

    PlantService plantService;
    PlantEventService plantEventService;
    FarmPlotService farmPlotService;
    FarmZoneService farmZoneService;

    PlantRepository plantRepository;
    PlantEventRepository plantEventRepository;
    FarmPlotRepository farmPlotRepository;
    FarmZoneRepository farmZoneRepository;

    @Override
    @Transactional
    public SyncPushResponse push(String profileId, SyncPushRequest request) {
        if (request == null || request.getMutations() == null) {
            return SyncPushResponse.builder()
                    .idMappings(List.of())
                    .serverTime(nowIso())
                    .results(List.of())
                    .build();
        }

        Map<String, String> idMap = new HashMap<>();
        if (request.getKnownIdMappings() != null) {
            for (SyncIdMapping m : request.getKnownIdMappings()) {
                if (m != null && StringUtils.hasText(m.getLocalId()) && StringUtils.hasText(m.getServerId())) {
                    idMap.put(m.getLocalId(), m.getServerId());
                }
            }
        }

        List<SyncIdMapping> newMappings = new ArrayList<>();
        List<SyncMutationResult> results = new ArrayList<>();

        for (SyncMutation mutation : request.getMutations()) {
            if (mutation == null) continue;

            String mutationId = mutation.getId();
            try {
                String table = safeLower(mutation.getTableName());
                String op = safeUpper(mutation.getOperation());

                String recordId = mutation.getRecordId();
                if (StringUtils.hasText(recordId) && idMap.containsKey(recordId)) {
                    recordId = idMap.get(recordId);
                }

                // Rewrite payload using current idMap
                Map<String, Object> payload = parsePayloadObject(mutation.getPayload());
                Map<String, Object> rewrittenPayload = rewriteIds(payload, idMap);

                switch (table) {
                    case "plants" -> applyPlantMutation(op, recordId, rewrittenPayload, idMap, newMappings);
                    case "plant_events" -> applyPlantEventMutation(op, recordId, rewrittenPayload, idMap, newMappings);
                    case "farm_plots" -> applyFarmPlotMutation(op, recordId, rewrittenPayload);
                    case "farm_zones" -> applyFarmZoneMutation(op, recordId, rewrittenPayload);
                    default -> throw new AppException(ErrorCode.INVALID_OPERATION);
                }

                results.add(SyncMutationResult.builder()
                        .mutationId(mutationId)
                        .applied(true)
                        .build());

            } catch (Exception e) {
                log.warn("sync push failed mutationId={} err={}", mutationId, e.getMessage());
                results.add(SyncMutationResult.builder()
                        .mutationId(mutationId)
                        .applied(false)
                        .errorMessage(e.getMessage())
                        .build());
            }
        }

        return SyncPushResponse.builder()
                .idMappings(newMappings)
                .serverTime(nowIso())
                .results(results)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SyncPullResponse pull(String profileId, SyncPullRequest request) {
        OffsetDateTime since = parseSince(request != null ? request.getSince() : null);

        // NOTE: for now we use lastModifiedAt >= since and owner/profile scoping
        List<FarmPlotResponse> plots = farmPlotRepository.findByOwnerProfileIdAndActiveTrue(profileId).stream()
                .filter(p -> p.getLastModifiedAt() != null && !p.getLastModifiedAt().isBefore(since.toLocalDateTime()))
                .map(p -> farmPlotService.getById(p.getId()))
                .toList();

        List<FarmZoneResponse> zones = farmZoneRepository.findByOwnerProfileIdAndActiveTrue(profileId).stream()
                .filter(z -> z.getLastModifiedAt() != null && !z.getLastModifiedAt().isBefore(since.toLocalDateTime()))
                .map(z -> farmZoneService.getById(z.getId()))
                .toList();

        List<PlantResponse> plants = plantRepository.findByOwnerProfileId(profileId, org.springframework.data.domain.Pageable.unpaged())
                .getContent()
                .stream()
                .filter(p -> p.getLastModifiedAt() != null && !p.getLastModifiedAt().isBefore(since.toLocalDateTime()))
                .map(p -> plantService.getPlantById(p.getId()))
                .toList();

        // PlantEvents: scope by user's plots/zones/plants would be better, but minimal viable uses owner via plot/zone/plant relationships.
        // Here we pull by plantIds owned by profile.
        List<String> plantIds = plantRepository.findByOwnerProfileId(profileId, org.springframework.data.domain.Pageable.unpaged())
                .getContent()
                .stream()
                .map(Plant::getId)
                .toList();

        List<PlantEventResponse> events = plantIds.isEmpty() ? List.of() : plantEventRepository.findByPlantIdIn(plantIds).stream()
                .filter(e -> e.getLastModifiedAt() != null && !e.getLastModifiedAt().isBefore(since.toLocalDateTime()))
                .map(e -> plantEventService.getEventById(e.getId()))
                .toList();

        return SyncPullResponse.builder()
                .serverTime(nowIso())
                .farmPlots(plots)
                .farmZones(zones)
                .plants(plants)
                .plantEvents(events)
                .build();
    }

    private void applyPlantMutation(String op, String recordId, Map<String, Object> payload,
                                   Map<String, String> idMap, List<SyncIdMapping> newMappings) throws Exception {
        if ("CREATE".equals(op)) {
            PlantCreateRequest req = objectMapper.convertValue(payload, PlantCreateRequest.class);

            // If client used UUID as recordId, create normally and return mapping
            PlantResponse created = plantService.createPlant(req);
            if (StringUtils.hasText(recordId) && !ObjectId.isValid(recordId)) {
                idMap.put(recordId, created.getId());
                newMappings.add(SyncIdMapping.builder()
                        .localId(recordId)
                        .serverId(created.getId())
                        .entityType("plants")
                        .build());
            }

        } else if ("UPDATE".equals(op)) {
            PlantUpdateRequest req = objectMapper.convertValue(payload, PlantUpdateRequest.class);
            plantService.updatePlant(requireObjectId(recordId), req);

        } else if ("DELETE".equals(op)) {
            // soft delete standardized: active=false (for now we hard-delete Plants, will be adjusted)
            plantService.deletePlant(requireObjectId(recordId));

        } else {
            throw new AppException(ErrorCode.INVALID_OPERATION);
        }
    }

    private void applyPlantEventMutation(String op, String recordId, Map<String, Object> payload,
                                        Map<String, String> idMap, List<SyncIdMapping> newMappings) throws Exception {
        if ("CREATE".equals(op)) {
            PlantEventCreateRequest req = objectMapper.convertValue(payload, PlantEventCreateRequest.class);

            // ensure plantId has been rewritten to ObjectId before hitting validation
            if (StringUtils.hasText(req.getPlantId()) && idMap.containsKey(req.getPlantId())) {
                req.setPlantId(idMap.get(req.getPlantId()));
            }

            PlantEventResponse created = plantEventService.createEvent(req);
            if (StringUtils.hasText(recordId) && !ObjectId.isValid(recordId)) {
                idMap.put(recordId, created.getId());
                newMappings.add(SyncIdMapping.builder()
                        .localId(recordId)
                        .serverId(created.getId())
                        .entityType("plant_events")
                        .build());
            }

        } else if ("UPDATE".equals(op)) {
            PlantEventUpdateRequest req = objectMapper.convertValue(payload, PlantEventUpdateRequest.class);
            plantEventService.updateEvent(requireObjectId(recordId), req);

        } else if ("DELETE".equals(op)) {
            plantEventService.deleteEvent(requireObjectId(recordId));

        } else {
            throw new AppException(ErrorCode.INVALID_OPERATION);
        }
    }

    private void applyFarmPlotMutation(String op, String recordId, Map<String, Object> payload) {
        if ("DELETE".equals(op)) {
            farmPlotService.softDelete(recordId);
            return;
        }
        // For now farm plot mutations are not used by offline module
        throw new AppException(ErrorCode.INVALID_OPERATION);
    }

    private void applyFarmZoneMutation(String op, String recordId, Map<String, Object> payload) {
        if ("DELETE".equals(op)) {
            farmZoneService.softDelete(recordId);
            return;
        }
        throw new AppException(ErrorCode.INVALID_OPERATION);
    }

    private Map<String, Object> parsePayloadObject(String payloadJson) throws Exception {
        if (!StringUtils.hasText(payloadJson)) return new HashMap<>();
        return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
    }

    private Map<String, Object> rewriteIds(Map<String, Object> payload, Map<String, String> idMap) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            out.put(e.getKey(), rewriteAny(e.getValue(), idMap));
        }
        return out;
    }

    private Object rewriteAny(Object value, Map<String, String> idMap) {
        if (value instanceof String s) {
            return idMap.getOrDefault(s, s);
        }
        if (value instanceof List<?> list) {
            List<Object> mapped = new ArrayList<>();
            for (Object item : list) mapped.add(rewriteAny(item, idMap));
            return mapped;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), rewriteAny(e.getValue(), idMap));
            }
            return out;
        }
        return value;
    }

    private String requireObjectId(String id) {
        if (!StringUtils.hasText(id) || !ObjectId.isValid(id)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        return id;
    }

    private OffsetDateTime parseSince(String since) {
        if (!StringUtils.hasText(since)) {
            // default: pull everything by using epoch
            return OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(since);
        } catch (Exception e) {
            // fallback: allow LocalDateTime without offset
            return OffsetDateTime.of(LocalDateTime.parse(since), ZoneOffset.UTC);
        }
    }

    private String nowIso() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private String safeLower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private String safeUpper(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}
