package com.leafy.iotmetricscollectorservice.dto.media;

import com.leafy.iotmetricscollectorservice.entity.Recurrence;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClientCameraScheduleRequest {
    private Boolean enabled;
    private LocalTime timeOfDay;
    private Recurrence recurrence;
    private String resolution;
    private String quality;
    private String uploadEndpoint;
}
