package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.dto.BootstrapRequest;
import com.leafy.iottestdataservice.dto.BootstrapResponse;

public interface SeedBootstrapService {

    BootstrapResponse bootstrapMinimal(BootstrapRequest request);

    BootstrapResponse bootstrapFull(BootstrapRequest request);
}
