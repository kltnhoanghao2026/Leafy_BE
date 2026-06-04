package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.AlertRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID>, JpaSpecificationExecutor<AlertRule> {

    List<AlertRule> findAllByEnabledTrueAndSensorTypeId(UUID sensorTypeId);

    List<AlertRule> findAllByDeviceIdAndOwnerUserId(UUID deviceId, String ownerUserId);

    Optional<AlertRule> findByIdAndOwnerUserId(UUID id, String ownerUserId);
}
