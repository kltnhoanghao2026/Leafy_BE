package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.dto.CameraCaptureManualRequest;
import com.leafy.iottestdataservice.dto.CameraCaptureScheduledRequest;
import com.leafy.iottestdataservice.dto.CameraCaptureSimulationResponse;

public interface CameraCaptureSimulationService {

    CameraCaptureSimulationResponse simulateManualCapture(CameraCaptureManualRequest request);

    CameraCaptureSimulationResponse scheduleCapture(CameraCaptureScheduledRequest request, boolean runNow);
}
