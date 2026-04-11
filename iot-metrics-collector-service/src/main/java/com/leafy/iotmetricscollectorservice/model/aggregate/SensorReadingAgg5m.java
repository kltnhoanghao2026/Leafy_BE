package com.leafy.iotmetricscollectorservice.model.aggregate;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "sensor_reading_agg_5m",
    indexes = {
        @Index(
            name = "idx_sensor_reading_agg_5m_device_sensor_bucket_desc",
            columnList = "device_id, sensor_type_id, bucket_start DESC"
        ),
        @Index(
            name = "idx_sensor_reading_agg_5m_zone_sensor_bucket_desc",
            columnList = "zone_id, sensor_type_id, bucket_start DESC"
        )
    }
)
public class SensorReadingAgg5m extends BaseSensorReadingAgg {
}
