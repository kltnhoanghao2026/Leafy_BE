package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.model.AlertRuleBootstrapResult;
import com.leafy.iottestdataservice.model.BootstrappedDevice;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AlertRuleBootstrapService {

    AlertRuleBootstrapResult bootstrapMinimalRules(Map<String, UUID> sensorTypeIds, List<BootstrappedDevice> devices);

    AlertRuleBootstrapResult bootstrapFullRules(Map<String, UUID> sensorTypeIds, List<BootstrappedDevice> devices);
}
