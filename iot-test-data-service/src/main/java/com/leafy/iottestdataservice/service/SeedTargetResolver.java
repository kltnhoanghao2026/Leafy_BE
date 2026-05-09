package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.dto.BootstrapRequest;
import com.leafy.iottestdataservice.model.SeedTarget;
import java.util.List;

public interface SeedTargetResolver {

    List<SeedTarget> resolveTargets(BootstrapRequest request, int maxTargets);
}
