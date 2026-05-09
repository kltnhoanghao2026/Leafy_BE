package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.model.ReferenceSeedResult;
import com.leafy.iottestdataservice.dto.BootstrapRequest;

public interface ReferenceSeedService {

    ReferenceSeedResult seedMinimalReferenceData();

    ReferenceSeedResult seedMinimalReferenceData(BootstrapRequest request);

    ReferenceSeedResult seedFullReferenceData();

    ReferenceSeedResult seedFullReferenceData(BootstrapRequest request);
}
