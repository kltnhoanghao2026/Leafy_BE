package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectResponse;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaAnalysis;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;

public interface ImageDiseaseAlertService {
    AlertEvent createDiseaseAlert(DeviceMediaEvent mediaEvent, DiseaseDetectResponse response);

    AlertEvent createDiseaseAlert(DeviceMediaEvent mediaEvent, DiseaseDetectResponse response, DeviceMediaAnalysis analysis);
}
