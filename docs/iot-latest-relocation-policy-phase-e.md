# IoT Latest Relocation Policy Phase E

## Policy

Delete `sensor_latest_readings` rows for a device when its assignment changes or when the device is released.

Latest readings represent the current operational state of a device in its current assignment. After relocation, old values from the previous zone should not represent the new zone.

## Trigger Events

| Event | Clear latest? |
| --- | ---: |
| Update name only | No |
| Update zone | Yes |
| Update farm/zone | Yes |
| Release device | Yes |
| Claim after release | Already cleared |
| New telemetry after assignment | Recreates latest |

## Data Not Deleted

The relocation policy only clears the latest-reading cache. It does not delete:

* raw readings
* aggregate readings
* media
* analysis
* alerts
* config
* camera schedules, except existing release behavior that disables schedules

## Implementation

`DeviceServiceImpl` clears latest readings through `SensorLatestReadingRepository.deleteByDeviceId(deviceId)` when the stored `farmPlot` or `zone` assignment changes, and when a device is released.

Telemetry ingest is unchanged. After the latest cache is cleared, the next telemetry sample recreates latest rows through the existing latest-reading aggregation flow.

## Known Limitations

* No stale marker or audit trail is stored for deleted latest rows.
* Device-history latest cache is intentionally reset.
* Historical chart data remains available through raw and aggregate endpoints.
