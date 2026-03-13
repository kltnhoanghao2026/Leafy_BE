"""
Environment State Node (Phase 0)

Fetches real-time environmental context for the target plant from IoT sensors:
  - Soil sensors (moisture, pH, NPK, temperature)
  - GPS / location data
  - Weather / microclimate readings

TODO: Replace hardcoded values with actual IoT API calls.
      Suggested integrations:
        - Soil: MQTT / REST call to sensor hub by plant_id or farm_plot_id
        - GPS:  PlantEvent or FarmPlot table lookup
        - Weather: OpenWeatherMap / aWhere API by lat/lon
"""

import logging
from app.agents.rag_state import GraphState

logger = logging.getLogger(__name__)


# ─────────────────────────────────────────────────────────────────────────────
# Hardcoded placeholder values — replace with real IoT reads later
# ─────────────────────────────────────────────────────────────────────────────
_PLACEHOLDER_ENV = {
    # --- Location ---
    "gps": {
        "latitude": 14.0583,       # Central Highlands, Vietnam
        "longitude": 108.2772,
        "farm_plot_id": "plot-001",
        "altitude_m": 750,
    },

    # --- Soil sensors ---
    "soil": {
        "moisture_pct": 62.4,      # % volumetric water content
        "temperature_c": 24.1,     # °C at 10 cm depth
        "ph": 5.8,                 # Ideal for coffee: 5.5–6.5
        "nitrogen_ppm": 38,        # N — parts per million
        "phosphorus_ppm": 12,      # P
        "potassium_ppm": 95,       # K
        "organic_matter_pct": 3.2,
    },

    # --- Weather / microclimate ---
    "weather": {
        "air_temp_c": 27.3,
        "humidity_pct": 81,        # High humidity → higher fungal disease risk
        "rainfall_mm_last_7d": 42,
        "wind_speed_kmh": 8,
        "uv_index": 6,
        "forecast_rain_24h": False, # Affects spray scheduling
    },

    # --- Data freshness ---
    "reading_timestamp": "2026-02-25T14:00:00+07:00",  # TODO: use datetime.now()
    "data_source": "HARDCODED_PLACEHOLDER",              # Change to "IOT_SENSOR" when live
}
# ─────────────────────────────────────────────────────────────────────────────


def fetch_env_state(state: GraphState) -> dict:
    """
    Fetch environmental context for the plant from IoT sensors.

    Currently returns hardcoded placeholder values.
    Replace the _PLACEHOLDER_ENV dict (or this function body) with
    actual sensor API calls keyed on plant_id / farm_plot_id.

    Args:
        state: Current graph state (question available for plant_id extraction if needed)

    Returns:
        Updated state with `env_state` dict containing soil, GPS, and weather readings.
    """
    logger.info("[ENV STATE] Fetching environment state (source: %s)", _PLACEHOLDER_ENV["data_source"])

    # TODO: extract plant_id from question and query real sensor API
    # e.g. env = sensor_api.get_latest(plant_id=extract_plant_id(state["question"]))

    env = dict(_PLACEHOLDER_ENV)  # shallow copy so we don't mutate the module-level dict

    logger.debug(
        "[ENV STATE] soil=pH%.1f moisture=%.0f%% | weather=%.1f°C humidity=%.0f%% rain_forecast=%s",
        env["soil"]["ph"],
        env["soil"]["moisture_pct"],
        env["weather"]["air_temp_c"],
        env["weather"]["humidity_pct"],
        env["weather"]["forecast_rain_24h"],
    )

    return {"env_state": env}
