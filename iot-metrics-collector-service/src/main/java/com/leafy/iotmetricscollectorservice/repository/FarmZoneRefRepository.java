package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmZoneRefRepository extends JpaRepository<FarmZoneRef, String> {
}
