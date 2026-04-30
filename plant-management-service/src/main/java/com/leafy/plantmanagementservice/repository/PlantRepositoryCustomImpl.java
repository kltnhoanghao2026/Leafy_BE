package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.enums.PlantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Repository
@RequiredArgsConstructor
public class PlantRepositoryCustomImpl implements PlantRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Plant> findPlantsByFilters(String search, String farmPlotId, String farmZoneId, String speciesId, PlantStatus status, Pageable pageable) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (StringUtils.hasText(search)) {
            String regex = ".*" + Pattern.quote(search.trim()) + ".*";
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("nickName").regex(regex, "i"),
                    Criteria.where("plantNumber").regex(regex, "i"),
                    Criteria.where("tagCode").regex(regex, "i"),
                    Criteria.where("batchNumber").regex(regex, "i")
            );
            criteriaList.add(searchCriteria);
        }

        // farmPlotId and farmZoneId are OR'd — a plant lives in one or the other
        boolean hasPlotFilter = StringUtils.hasText(farmPlotId);
        boolean hasZoneFilter = StringUtils.hasText(farmZoneId);
        if (hasPlotFilter && hasZoneFilter) {
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("farmPlotId").is(farmPlotId.trim()),
                    Criteria.where("farmZoneId").is(farmZoneId.trim())
            ));
        } else if (hasPlotFilter) {
            criteriaList.add(Criteria.where("farmPlotId").is(farmPlotId.trim()));
        } else if (hasZoneFilter) {
            criteriaList.add(Criteria.where("farmZoneId").is(farmZoneId.trim()));
        }

        if (StringUtils.hasText(speciesId)) {
            criteriaList.add(Criteria.where("speciesId").is(speciesId.trim()));
        }

        if (status != null) {
            criteriaList.add(Criteria.where("plantStatus").is(status));
        }

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, Plant.class);

        query.with(pageable);
        List<Plant> results = mongoTemplate.find(query, Plant.class);

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public Page<Plant> findMyPlants(List<String> userFarmPlotIds, List<String> userFarmZoneIds,
                                    String search, String farmPlotId, String farmZoneId,
                                    String speciesId, PlantStatus status, Pageable pageable) {

        boolean hasPlots = userFarmPlotIds != null && !userFarmPlotIds.isEmpty();
        boolean hasZones = userFarmZoneIds != null && !userFarmZoneIds.isEmpty();

        // User owns nothing — return empty immediately
        if (!hasPlots && !hasZones) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<Criteria> criteriaList = new ArrayList<>();

        // ── Ownership scope ───────────────────────────────────────────────────
        // A plant belongs to the user via its farmPlotId OR its farmZoneId —
        // these are mutually exclusive on a per-plant basis (a plant has one or
        // the other as its primary location). Never AND them.
        boolean filterByPlot = StringUtils.hasText(farmPlotId);
        boolean filterByZone = StringUtils.hasText(farmZoneId);

        if (filterByPlot || filterByZone) {
            // Build OR of whichever filter params were supplied, after ownership validation.
            List<Criteria> filterOr = new ArrayList<>();

            if (filterByPlot) {
                String plotTrimmed = farmPlotId.trim();
                if (hasPlots && userFarmPlotIds.contains(plotTrimmed)) {
                    filterOr.add(Criteria.where("farmPlotId").is(plotTrimmed));
                }
                // else: requested plot not owned — skip (don't add, may still match via zone below)
            }

            if (filterByZone) {
                String zoneTrimmed = farmZoneId.trim();
                if (hasZones && userFarmZoneIds.contains(zoneTrimmed)) {
                    filterOr.add(Criteria.where("farmZoneId").is(zoneTrimmed));
                }
                // else: requested zone not owned — skip
            }

            if (filterOr.isEmpty()) {
                // Neither supplied filter belongs to this user
                return new PageImpl<>(List.of(), pageable, 0);
            }

            // Single filter → direct equality; two filters → OR between them
            criteriaList.add(filterOr.size() == 1
                    ? filterOr.get(0)
                    : new Criteria().orOperator(filterOr.toArray(new Criteria[0])));

        } else {
            // No filter supplied: broad scope — plant belongs to user via plot OR via zone
            List<Criteria> ownershipOr = new ArrayList<>();
            if (hasPlots) {
                ownershipOr.add(Criteria.where("farmPlotId").in(userFarmPlotIds));
            }
            if (hasZones) {
                ownershipOr.add(Criteria.where("farmZoneId").in(userFarmZoneIds));
            }
            criteriaList.add(new Criteria().orOperator(ownershipOr.toArray(new Criteria[0])));
        }

        // ── Additional narrowing filters ──────────────────────────────────────
        if (StringUtils.hasText(search)) {
            String regex = ".*" + Pattern.quote(search.trim()) + ".*";
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("nickName").regex(regex, "i"),
                    Criteria.where("plantNumber").regex(regex, "i"),
                    Criteria.where("tagCode").regex(regex, "i"),
                    Criteria.where("batchNumber").regex(regex, "i")
            ));
        }

        if (StringUtils.hasText(speciesId)) {
            criteriaList.add(Criteria.where("speciesId").is(speciesId.trim()));
        }

        if (status != null) {
            criteriaList.add(Criteria.where("plantStatus").is(status));
        }

        Query query = new Query();
        query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));

        long total = mongoTemplate.count(query, Plant.class);

        query.with(pageable);
        List<Plant> results = mongoTemplate.find(query, Plant.class);

        return new PageImpl<>(results, pageable, total);
    }

}

