package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.enums.EventType;
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

@Repository
@RequiredArgsConstructor
public class PlantEventRepositoryCustomImpl implements PlantEventRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    // ── Admin multi-criteria filter ───────────────────────────────────────────

    @Override
    public Page<PlantEvent> findAllByFilters(
            EventType eventType,
            Boolean planned,
            String farmPlotId,
            String farmZoneId,
            Pageable pageable
    ) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (eventType != null) {
            criteriaList.add(Criteria.where("eventType").is(eventType));
        }
        if (planned != null) {
            criteriaList.add(Criteria.where("planned").is(planned));
        }

        // farmPlotId and farmZoneId are OR'd — an event is attached to one or the other
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

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, PlantEvent.class);
        query.with(pageable);
        List<PlantEvent> results = mongoTemplate.find(query, PlantEvent.class);
        return new PageImpl<>(results, pageable, total);
    }

    // ── Profile calendar events (broad ownership scope) ───────────────────────

    @Override
    public List<PlantEvent> findProfileCalendarEvents(
            List<String> farmPlotIds,
            List<String> farmZoneIds,
            List<String> plantIds,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
        Criteria dateCriteria = new Criteria().andOperator(
                Criteria.where("calculatedStartDate").lte(endDate),
                Criteria.where("calculatedEndDate").gte(startDate)
        );

        List<Criteria> orCriterias = new ArrayList<>();
        if (farmPlotIds != null && !farmPlotIds.isEmpty()) {
            orCriterias.add(Criteria.where("farmPlotId").in(farmPlotIds));
        }
        if (farmZoneIds != null && !farmZoneIds.isEmpty()) {
            orCriterias.add(Criteria.where("farmZoneId").in(farmZoneIds));
        }
        if (plantIds != null && !plantIds.isEmpty()) {
            orCriterias.add(Criteria.where("plantId").in(plantIds));
        }

        if (orCriterias.isEmpty()) {
            return new ArrayList<>();
        }

        Criteria targetCriteria = new Criteria().orOperator(orCriterias.toArray(new Criteria[0]));
        Query query = new Query(new Criteria().andOperator(dateCriteria, targetCriteria));
        return mongoTemplate.find(query, PlantEvent.class);
    }

    // ── Calendar events by specific plot OR zone (both filters given) ─────────

    @Override
    public List<PlantEvent> findByPlotOrZoneAndDateRange(
            String farmPlotId,
            String farmZoneId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
        Criteria dateCriteria = new Criteria().andOperator(
                Criteria.where("calculatedStartDate").lte(endDate),
                Criteria.where("calculatedEndDate").gte(startDate)
        );
        Criteria locationCriteria = new Criteria().orOperator(
                Criteria.where("farmPlotId").is(farmPlotId.trim()),
                Criteria.where("farmZoneId").is(farmZoneId.trim())
        );
        Query query = new Query(new Criteria().andOperator(dateCriteria, locationCriteria));
        return mongoTemplate.find(query, PlantEvent.class);
    }
}
