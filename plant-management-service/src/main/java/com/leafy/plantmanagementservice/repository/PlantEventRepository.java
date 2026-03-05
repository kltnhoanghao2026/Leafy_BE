package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.enums.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlantEventRepository extends MongoRepository<PlantEvent, String> {

    Page<PlantEvent> findByPlantId(String plantId, Pageable pageable);

    Page<PlantEvent> findByPlantIdAndEventType(String plantId, EventType eventType, Pageable pageable);

    Page<PlantEvent> findByPlantIdAndPlanned(String plantId, boolean planned, Pageable pageable);

    Page<PlantEvent> findBySourcePlanId(String sourcePlanId, Pageable pageable);
}
