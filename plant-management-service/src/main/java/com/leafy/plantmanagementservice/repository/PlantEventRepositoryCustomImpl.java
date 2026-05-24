package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;

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

    // ── Shared date-overlap criteria ─────────────────────────────────────────
    //
    // An event overlaps [startDate, endDate] when:
    //   calculatedStartDate <= endDate  AND  calculatedEndDate >= startDate
    //
    // Events with null calculatedEndDate are treated as single-day events:
    // they are included when calculatedStartDate falls inside [startDate, endDate].
    //
    private Criteria buildDateOverlapCriteria(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        // Events that have an explicit end date and overlap the range
        Criteria rangedEvent = new Criteria().andOperator(
                Criteria.where("calculatedStartDate").ne(null).lte(endDate),
                Criteria.where("calculatedEndDate").ne(null).gte(startDate)
        );
        // Single-day events (no end date) whose start date falls inside the range
        Criteria singleDayEvent = new Criteria().andOperator(
                Criteria.where("calculatedStartDate").gte(startDate).lte(endDate),
                new Criteria().orOperator(
                        Criteria.where("calculatedEndDate").exists(false),
                        Criteria.where("calculatedEndDate").is(null)
                )
        );
        return new Criteria().orOperator(rangedEvent, singleDayEvent);
    }

    // ── Profile calendar events (broad ownership scope) ───────────────────────

    @Override
    public List<PlantEvent> findProfileCalendarEvents(
            List<String> farmPlotIds,
            List<String> farmZoneIds,
            List<String> plantIds,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            TargetType targetType
    ) {
        Criteria dateCriteria = buildDateOverlapCriteria(startDate, endDate);

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

        // Apply TargetType filter on top of the ownership scope
        List<Criteria> allCriteria = new ArrayList<>();
        allCriteria.add(dateCriteria);
        allCriteria.add(targetCriteria);

        if (targetType != null) {
            switch (targetType) {
                case FARM -> {
                    // FARM-scope: event has farmPlotId set, no farmZoneId, no plantId
                    allCriteria.add(Criteria.where("farmPlotId").ne(null));
                    allCriteria.add(Criteria.where("farmZoneId").is(null));
                    allCriteria.add(Criteria.where("plantId").is(null));
                }
                case FARM_ZONE -> {
                    // FARM_ZONE-scope: event has farmZoneId set
                    allCriteria.add(Criteria.where("farmZoneId").ne(null));
                }
                case PLANT -> {
                    // PLANT-scope: event has plantId set
                    allCriteria.add(Criteria.where("plantId").ne(null));
                }
            }
        }

        Query query = new Query(new Criteria().andOperator(allCriteria.toArray(new Criteria[0])));
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
        Criteria dateCriteria = buildDateOverlapCriteria(startDate, endDate);
        Criteria locationCriteria = new Criteria().orOperator(
                Criteria.where("farmPlotId").is(farmPlotId.trim()),
                Criteria.where("farmZoneId").is(farmZoneId.trim())
        );
        Query query = new Query(new Criteria().andOperator(dateCriteria, locationCriteria));
        return mongoTemplate.find(query, PlantEvent.class);
    }

    // ── Entity-scoped calendar helpers ────────────────────────────────────────

    @Override
    public List<PlantEvent> findByPlantIdAndDateRange(
            String plantId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
        Criteria criteria = new Criteria().andOperator(
                buildDateOverlapCriteria(startDate, endDate),
                Criteria.where("plantId").is(plantId)
        );
        return mongoTemplate.find(new Query(criteria), PlantEvent.class);
    }

    @Override
    public List<PlantEvent> findByFarmPlotIdAndDateRange(
            String farmPlotId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
        Criteria criteria = new Criteria().andOperator(
                buildDateOverlapCriteria(startDate, endDate),
                Criteria.where("farmPlotId").is(farmPlotId)
        );
        return mongoTemplate.find(new Query(criteria), PlantEvent.class);
    }

    @Override
    public List<PlantEvent> findByFarmZoneIdAndDateRange(
            String farmZoneId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
        Criteria criteria = new Criteria().andOperator(
                buildDateOverlapCriteria(startDate, endDate),
                Criteria.where("farmZoneId").is(farmZoneId)
        );
        return mongoTemplate.find(new Query(criteria), PlantEvent.class);
    }



    @Override
    public List<PlantEvent> findByPlanApplyIdAndDateRange(
            String planApplyId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
        Criteria criteria = new Criteria().andOperator(
                buildDateOverlapCriteria(startDate, endDate),
                Criteria.where("planApplyId").is(planApplyId)
        );
        return mongoTemplate.find(new Query(criteria), PlantEvent.class);
    }

    // ── Stats aggregation methods ─────────────────────────────────────────────

    /**
     * Build the ownership-scope criteria: event belongs to user if its farmPlotId, farmZoneId,
     * or plantId matches one of the user's IDs.
     */
    private Criteria buildProfileScopeCriteria(
            List<String> farmPlotIds, List<String> farmZoneIds, List<String> plantIds) {
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
            // match nothing
            return Criteria.where("_id").is(null);
        }
        return new Criteria().orOperator(orCriterias.toArray(new Criteria[0]));
    }

    @Override
    public Map<String, Long> countByEventTypeForProfile(
            List<String> farmPlotIds, List<String> farmZoneIds, List<String> plantIds) {
        Criteria scopeCriteria = buildProfileScopeCriteria(farmPlotIds, farmZoneIds, plantIds);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(scopeCriteria),
                Aggregation.group("eventType").count().as("count")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation, "plant_events", Document.class);

        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (Document doc : results.getMappedResults()) {
            String type = doc.getString("_id");
            Long count = doc.get("count", Number.class).longValue();
            if (type != null) {
                breakdown.put(type, count);
            }
        }
        return breakdown;
    }

    @Override
    public Map<String, Long> countByEventTypeForProfile(
            List<String> farmPlotIds, List<String> farmZoneIds, List<String> plantIds,
            LocalDate startDate, LocalDate endDate) {
        Criteria scopeCriteria = buildProfileScopeCriteria(farmPlotIds, farmZoneIds, plantIds);
        Criteria dateCriteria = buildDateOverlapCriteria(startDate, endDate);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(new Criteria().andOperator(scopeCriteria, dateCriteria)),
                Aggregation.group("eventType").count().as("count")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation, "plant_events", Document.class);

        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (Document doc : results.getMappedResults()) {
            String type = doc.getString("_id");
            Long count = doc.get("count", Number.class).longValue();
            if (type != null) {
                breakdown.put(type, count);
            }
        }
        return breakdown;
    }

    @Override
    public long countProfileEventsFiltered(
            List<String> farmPlotIds, List<String> farmZoneIds, List<String> plantIds,
            LocalDate startDate, LocalDate endDate, Boolean completed, boolean overdue) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(buildProfileScopeCriteria(farmPlotIds, farmZoneIds, plantIds));

        if (overdue) {
            // Overdue: calculatedEndDate < today AND completed = false
            criteriaList.add(Criteria.where("calculatedEndDate").lt(LocalDate.now()));
            criteriaList.add(Criteria.where("completed").is(false));
        } else {
            if (startDate != null && endDate != null) {
                criteriaList.add(buildDateOverlapCriteria(startDate, endDate));
            }
            if (completed != null) {
                criteriaList.add(Criteria.where("completed").is(completed));
            }
        }

        Query query = new Query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        return mongoTemplate.count(query, PlantEvent.class);
    }

    @Override
    public List<PlantEvent> findRecentProfileEvents(
            List<String> farmPlotIds, List<String> farmZoneIds, List<String> plantIds, int limit) {
        Criteria scopeCriteria = buildProfileScopeCriteria(farmPlotIds, farmZoneIds, plantIds);
        Query query = new Query(scopeCriteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(limit);
        return mongoTemplate.find(query, PlantEvent.class);
    }
}
