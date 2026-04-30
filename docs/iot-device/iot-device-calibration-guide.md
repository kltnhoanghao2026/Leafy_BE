# IoT Device Calibration Guide

This guide covers practical bench calibration for the Leafy ESP32 firmware sensors that need local raw ADC references:

- YL-69 soil moisture probe
- LDR/photoresistor light sensor

DHT11 temperature and humidity do not need a custom calibration workflow in this phase. If a batch offset is discovered later, add that as a separate firmware feature.

## Enable Calibration Logs

For a calibration pass, set these build flags in `Leafy_IoT/platformio.ini` or in a local PlatformIO override:

```ini
-D LEAFY_CALIBRATION_LOGGING=1
-D LEAFY_CALIBRATION_LOG_INTERVAL_SEC=10
```

Flash the board and open serial monitor at `115200`.

Expected log shape:

```text
[INFO] Calibration sample: soilRaw=3180, soilPct=1.0, lightRaw=3440, lightNorm=20.7, soilCal=3200/1200, lightCal=3500/500
```

During calibration, focus on raw values:

- `soilRaw`
- `lightRaw`

The normalized values are useful only after calibration constants are close to the real hardware.

## Soil Moisture Calibration

The YL-69 firmware normalizes soil moisture to `0..100` percent using:

- `soilDryRaw`
- `soilWetRaw`

The default assumptions are:

```ini
-D LEAFY_SOIL_DRY_RAW=3200
-D LEAFY_SOIL_WET_RAW=1200
```

Those defaults are placeholders. Real values depend on the probe, wiring, power voltage, ADC pin, soil, and corrosion state.

### What "Dry" Means

Use one of these references:

- Probe clean and dry in air.
- Probe inserted into dry reference soil.
- Probe inserted into the driest soil condition you want the app to treat as 0 percent.

For farm use, dry reference soil is usually better than air because it matches actual deployment better.

### What "Wet" Means

Use one of these references:

- Probe inserted into water-saturated soil.
- Probe inserted into the wettest soil condition you want the app to treat as 100 percent.

Avoid using plain water unless the device will be interpreted against that reference. Saturated soil is usually more realistic.

### Procedure

1. Enable calibration logs.
2. Place the probe in the dry reference.
3. Wait for readings to settle.
4. Record at least 10 `soilRaw` values.
5. Average the stable readings. Use this as `soilDryRaw`.
6. Move the probe to the wet reference.
7. Wait for readings to settle.
8. Record at least 10 `soilRaw` values.
9. Average the stable readings. Use this as `soilWetRaw`.
10. Update build flags or NVS calibration values.
11. Reflash or reload config.
12. Verify dry reference reports near 0.
13. Verify wet reference reports near 100.

### Example

Observed dry readings:

```text
3198, 3210, 3204, 3189, 3201
```

Use:

```ini
-D LEAFY_SOIL_DRY_RAW=3200
```

Observed wet readings:

```text
1188, 1210, 1196, 1204, 1199
```

Use:

```ini
-D LEAFY_SOIL_WET_RAW=1200
```

The current firmware supports decreasing raw values as moisture increases. If your circuit produces the opposite direction, choose dry and wet values accordingly; the normalization code handles either order.

## Light Calibration

The LDR firmware reports `LIGHT_INTENSITY` as a normalized `0..1000` brightness scale. It is not lux.

The firmware uses:

- `lightDarkRaw`
- `lightBrightRaw`

Default assumptions:

```ini
-D LEAFY_LIGHT_DARK_RAW=3500
-D LEAFY_LIGHT_BRIGHT_RAW=500
```

Those defaults assume raw ADC decreases as light increases. Many voltage divider layouts behave this way, but not all.

### What "Dark" Means

Use one of these references:

- LDR fully covered.
- Device in the darkest expected enclosure condition.
- Nighttime or low-light condition you want to map near 0.

The best reference is the darkest condition expected in the real installation.

### What "Bright" Means

Use one of these references:

- Device in the brightest expected deployment condition.
- Grow light at maximum expected intensity.
- Outdoor shade or sun condition, depending on deployment.

Do not calibrate to direct sun if the device will normally sit under a canopy or inside an enclosure.

### Procedure

1. Enable calibration logs.
2. Put the LDR in the dark reference condition.
3. Record at least 10 `lightRaw` values.
4. Average stable readings. Use this as `lightDarkRaw`.
5. Put the LDR in the bright reference condition.
6. Record at least 10 `lightRaw` values.
7. Average stable readings. Use this as `lightBrightRaw`.
8. Update build flags or NVS calibration values.
9. Reflash or reload config.
10. Verify dark reads near 0.
11. Verify bright reads near 1000.

### Example

Observed dark readings:

```text
3460, 3502, 3488, 3510, 3496
```

Use:

```ini
-D LEAFY_LIGHT_DARK_RAW=3490
```

Observed bright readings:

```text
480, 510, 525, 495, 500
```

Use:

```ini
-D LEAFY_LIGHT_BRIGHT_RAW=500
```

## Practical Caveats

YL-69 corrosion:

- The probe corrodes when powered continuously.
- Prefer `LEAFY_SOIL_POWER_PIN >= 0` so firmware powers it only during measurement.
- If no power-control pin is wired, document the risk and expect drift over time.

Analog noise:

- Use stable 3.3 V power.
- Keep analog wires short.
- Avoid routing analog wires near Wi-Fi antenna or switching regulators.
- Average multiple readings and ignore obvious outliers.

ADC behavior:

- ESP32 ADC values are not laboratory-grade measurements.
- Different pins and boards can produce different raw ranges.
- Calibration values are board-specific.

LDR placement:

- The enclosure changes readings.
- Calibrate with the LDR in its final housing, not loose on the desk.
- Do not compare normalized values between devices unless both are calibrated similarly.

Repeated measurements:

- Record multiple raw values.
- Use an average or median of stable values.
- Repeat calibration after changing wiring, board, probe, enclosure, or power supply.

## Recording Template

| Sensor | Condition | Raw readings | Chosen value |
| --- | --- | --- | --- |
| YL-69 | Dry | | `soilDryRaw=` |
| YL-69 | Wet | | `soilWetRaw=` |
| LDR | Dark | | `lightDarkRaw=` |
| LDR | Bright | | `lightBrightRaw=` |

## Verification

After applying calibration:

- Dry soil should publish `SOIL_MOISTURE` near 0.
- Wet soil should publish `SOIL_MOISTURE` near 100.
- Dark light reference should publish `LIGHT_INTENSITY` near 0.
- Bright light reference should publish `LIGHT_INTENSITY` near 1000.

Do not publish raw ADC values to the backend as metrics. Raw ADC values are only for local calibration.
