package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.model.ReferenceSeedResult;

public interface ReferenceSeedService {

    ReferenceSeedResult seedMinimalReferenceData();

    ReferenceSeedResult seedFullReferenceData();
}
