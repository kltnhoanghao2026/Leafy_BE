package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.dto.OperationResponse;
import com.leafy.iottestdataservice.dto.SimulationStartRequest;
import com.leafy.iottestdataservice.dto.SimulationStatusResponse;

public interface SimulationService {

    SimulationStatusResponse startSimulation(SimulationStartRequest request);

    OperationResponse stopSimulation();

    SimulationStatusResponse getStatus();
}
