package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.ref.FarmPlotRef;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmPlotRefRepository extends JpaRepository<FarmPlotRef, String> {
}
