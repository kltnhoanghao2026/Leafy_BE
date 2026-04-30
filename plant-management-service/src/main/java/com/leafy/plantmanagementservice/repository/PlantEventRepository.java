package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.enums.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PlantEventRepository extends MongoRepository<PlantEvent, String>, PlantEventRepositoryCustom {

    Page<PlantEvent> findByPlantId(String plantId, Pageable pageable);

    Page<PlantEvent> findByPlantIdAndEventType(String plantId, EventType eventType, Pageable pageable);

    Page<PlantEvent> findByPlantIdAndPlanned(String plantId, boolean planned, Pageable pageable);

    Page<PlantEvent> findBySourcePlanId(String sourcePlanId, Pageable pageable);

    Page<PlantEvent> findByFarmPlotId(String farmPlotId, Pageable pageable);

    Page<PlantEvent> findByFarmZoneId(String farmZoneId, Pageable pageable);

    Page<PlantEvent> findByEventType(EventType eventType, Pageable pageable);

    List<PlantEvent> findByFarmPlotIdAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
            String farmPlotId, LocalDate rangeEnd, LocalDate rangeStart);

    List<PlantEvent> findByFarmZoneIdAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
            String farmZoneId, LocalDate rangeEnd, LocalDate rangeStart);

    List<PlantEvent> findByPlantIdAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
            String plantId, LocalDate rangeEnd, LocalDate rangeStart);

    List<PlantEvent> findByFarmPlotIdInAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
            List<String> farmPlotIds, LocalDate rangeEnd, LocalDate rangeStart);
}
