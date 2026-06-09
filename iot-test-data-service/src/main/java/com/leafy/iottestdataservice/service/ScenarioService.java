package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.dto.ConfigAckScenarioRequest;
import com.leafy.iottestdataservice.dto.ConfigAckScenarioResponse;
import com.leafy.iottestdataservice.dto.ScenarioRequest;
import com.leafy.iottestdataservice.dto.ScenarioTriggerResponse;

public interface ScenarioService {

    ScenarioTriggerResponse triggerHighTemperature(ScenarioRequest request);

    ScenarioTriggerResponse triggerLowSoilMoisture(ScenarioRequest request);

    ConfigAckScenarioResponse triggerConfigAckSuccess(ConfigAckScenarioRequest request);

    ConfigAckScenarioResponse triggerConfigAckFailure(ConfigAckScenarioRequest request);
}
