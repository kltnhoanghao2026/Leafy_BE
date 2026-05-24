"""Environment State Node (Phase 0).

Resolves plant -> farm plot -> zone through API Gateway, then fetches IoT zone
overview to build real environmental context for downstream prompts.
"""

import logging
import re
from datetime import datetime, timezone
from typing import Any, Dict, Iterable, Optional

from app.agents.rag_state import GraphState
from app.services.env_gateway_client import EnvGatewayClient, get_env_gateway_client

logger = logging.getLogger(__name__)


_UUID_RE = re.compile(
    r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\b"
)
_OBJECT_ID_RE = re.compile(r"\b[a-fA-F0-9]{24}\b")
_PLANT_ID_RE = re.compile(
    r"\bplant(?:\s*[_-]?\s*id)?\s*[:=#-]?\s*([A-Za-z0-9][A-Za-z0-9_-]{2,63})\b",
    re.IGNORECASE,
)


_SOIL_MOISTURE_CODES = ("SOIL_MOISTURE", "MOISTURE", "SOIL_MOISTURE_PCT")
_SOIL_TEMP_CODES = ("SOIL_TEMP", "SOIL_TEMPERATURE", "SOIL_TEMP_C")
_SOIL_PH_CODES = ("SOIL_PH", "PH", "SOIL_ACIDITY")
_NITROGEN_CODES = ("N", "NITROGEN", "NITROGEN_PPM", "SOIL_N")
_PHOSPHORUS_CODES = ("P", "PHOSPHORUS", "PHOSPHORUS_PPM", "SOIL_P")
_POTASSIUM_CODES = ("K", "POTASSIUM", "POTASSIUM_PPM", "SOIL_K")
_ORGANIC_MATTER_CODES = (
    "ORGANIC_MATTER", "ORGANIC_MATTER_PCT", "OM", "OM_PCT",
    "SOM", "SOM_PCT", "SOIL_OM", "SOIL_OM_PCT", "HUMUS",
)

_AIR_TEMP_CODES = ("AIR_TEMP", "AIR_TEMPERATURE", "TEMP", "TEMPERATURE")
_HUMIDITY_CODES = ("HUMIDITY", "AIR_HUMIDITY", "RH")
_RAINFALL_CODES = ("RAINFALL_7D", "RAIN_7D", "RAINFALL")
_WIND_CODES = ("WIND_SPEED", "WIND_SPEED_KMH", "WIND")
_UV_CODES = ("UV", "UV_INDEX")
_FORECAST_RAIN_CODES = (
    "FORECAST_RAIN_24H", "FORECAST_RAIN", "PRECIP_24H", "RAIN_24H", "RAIN_FORECAST"
)


def _extract_plant_id(state: GraphState) -> Optional[str]:
    state_plant_id = state.get("plant_id")
    if isinstance(state_plant_id, str) and state_plant_id.strip():
        return state_plant_id.strip()

    question = state.get("question")
    if not isinstance(question, str) or not question.strip():
        return None

    explicit_match = _PLANT_ID_RE.search(question)
    if explicit_match:
        return explicit_match.group(1)

    uuid_match = _UUID_RE.search(question)
    if uuid_match:
        return uuid_match.group(0)

    object_id_match = _OBJECT_ID_RE.search(question)
    if object_id_match:
        return object_id_match.group(0)

    return None


def _to_float(value: Any) -> Optional[float]:
    try:
        if value is None:
            return None
        return float(value)
    except (TypeError, ValueError):
        return None


def _build_reading_index(readings: Iterable[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
    index: Dict[str, Dict[str, Any]] = {}
    for item in readings:
        if not isinstance(item, dict):
            continue
        code = str(item.get("sensorCode") or "").strip().upper()
        if code:
            index[code] = item
    return index


def _pick_value(reading_index: Dict[str, Dict[str, Any]], aliases: Iterable[str]) -> Optional[float]:
    for code in aliases:
        item = reading_index.get(code)
        if item is not None:
            return _to_float(item.get("value"))
    return None


def _resolve_reading_timestamp(overview: Dict[str, Any], readings: Iterable[Dict[str, Any]]) -> str:
    timestamp = overview.get("lastUpdatedAt")
    if isinstance(timestamp, str) and timestamp.strip():
        return timestamp

    latest_reading_time: Optional[str] = None
    for item in readings:
        reading_time = item.get("readingTime")
        if isinstance(reading_time, str) and reading_time.strip():
            if latest_reading_time is None or reading_time > latest_reading_time:
                latest_reading_time = reading_time

    if latest_reading_time:
        return latest_reading_time

    return datetime.now(timezone.utc).isoformat()


def _map_overview_to_env_state(zone_context: Dict[str, Any], overview: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    latest_readings = overview.get("latestReadings")
    if not isinstance(latest_readings, list) or not latest_readings:
        logger.info("[ENV STATE] No latest readings available for zone_id=%s", zone_context.get("zone_id"))
        return None

    reading_index = _build_reading_index(latest_readings)

    env_state = {
        "gps": {
            "latitude": zone_context.get("plot_latitude"),
            "longitude": zone_context.get("plot_longitude"),
            "farm_plot_id": zone_context.get("farm_plot_id"),
            "farm_zone_id": zone_context.get("zone_id"),
            "altitude_m": zone_context.get("altitude_m"),
        },
        "farm_info": {
            "plot_name": zone_context.get("plot_name"),
            "plot_code": zone_context.get("plot_code"),
            "plot_area_m2": zone_context.get("plot_area_m2"),
            "plot_address": zone_context.get("plot_address"),
            "zone_name": zone_context.get("zone_name"),
            "zone_code": zone_context.get("zone_code"),
            "zone_area_m2": zone_context.get("zone_area_m2"),
            "soil_type": zone_context.get("soil_type"),
            "crop_type": zone_context.get("crop_type"),
        },
        "soil": {
            "moisture_pct": _pick_value(reading_index, _SOIL_MOISTURE_CODES),
            "temperature_c": _pick_value(reading_index, _SOIL_TEMP_CODES),
            "ph": _pick_value(reading_index, _SOIL_PH_CODES),
            "nitrogen_ppm": _pick_value(reading_index, _NITROGEN_CODES),
            "phosphorus_ppm": _pick_value(reading_index, _PHOSPHORUS_CODES),
            "potassium_ppm": _pick_value(reading_index, _POTASSIUM_CODES),
            "organic_matter_pct": _pick_value(reading_index, _ORGANIC_MATTER_CODES),
        },
        "weather": {
            "air_temp_c": _pick_value(reading_index, _AIR_TEMP_CODES),
            "humidity_pct": _pick_value(reading_index, _HUMIDITY_CODES),
            "rainfall_mm_last_7d": _pick_value(reading_index, _RAINFALL_CODES),
            "wind_speed_kmh": _pick_value(reading_index, _WIND_CODES),
            "uv_index": _pick_value(reading_index, _UV_CODES),
            "forecast_rain_24h": _pick_value(reading_index, _FORECAST_RAIN_CODES),
        },
        "reading_timestamp": _resolve_reading_timestamp(overview, latest_readings),
        "data_source": "IOT_SENSOR",
    }
    return env_state

def fetch_env_state(state: GraphState) -> dict:
    """Fetch real environmental context from IoT metrics via API Gateway.

    Resolution priority:
    1. plant_id (extracted from state or question) → plant → farm plot → zone
    2. farm_zone_id (passed explicitly in the chat request) → zone directly
    3. farm_plot_id (passed explicitly in the chat request) → first zone in plot

    If none of the above resolve to a zone, or IoT data is unavailable,
    this node returns no env context for the turn and allows the graph to continue.
    """
    auth_header = state.get("authorization")
    if not isinstance(auth_header, str) or not auth_header.strip():
        logger.info("[ENV STATE] Missing Authorization header; skipping env context")
        return {"env_state": None}

    client = get_env_gateway_client()

    # --- Priority 1: resolve via plant_id ---
    plant_id = _extract_plant_id(state)
    if plant_id:
        zone_context, all_zone_ids = client.resolve_zone_context(
            plant_id=plant_id, auth_header=auth_header
        )
        if zone_context is not None:
            return _fetch_and_build_with_fallback(client, zone_context, all_zone_ids, auth_header)
        logger.info("[ENV STATE] plant_id=%s did not resolve to a zone; trying fallbacks", plant_id)

    # --- Priority 2: use caller-supplied farm_zone_id directly ---
    farm_zone_id = state.get("farm_zone_id")
    if isinstance(farm_zone_id, str) and farm_zone_id.strip():
        zone_context = client.resolve_zone_context_from_zone(
            farm_zone_id=farm_zone_id,
            auth_header=auth_header,
            farm_plot_id=state.get("farm_plot_id"),
            plant_id=plant_id,
        )
        return _fetch_and_build(client, zone_context, auth_header)

    # --- Priority 3: resolve via caller-supplied farm_plot_id ---
    farm_plot_id = state.get("farm_plot_id")
    if isinstance(farm_plot_id, str) and farm_plot_id.strip():
        zone_context, all_zone_ids = client.resolve_zone_context_from_plot(
            farm_plot_id=farm_plot_id,
            auth_header=auth_header,
            plant_id=plant_id,
        )
        if zone_context is not None:
            return _fetch_and_build_with_fallback(client, zone_context, all_zone_ids, auth_header)
        logger.info("[ENV STATE] farm_plot_id=%s did not resolve to a zone", farm_plot_id)

    logger.info("[ENV STATE] No plant_id, farm_zone_id, or farm_plot_id available; skipping env context")
    return {"env_state": None}


def _fetch_and_build(
    client: "EnvGatewayClient",
    zone_context: Dict[str, Any],
    auth_header: str,
    all_zone_ids: Optional[List[str]] = None,
) -> dict:
    """Fetch the IoT zone overview and map it to an env_state dict."""
    zone_id = zone_context["zone_id"]
    overview = client.get_zone_overview(zone_id=zone_id, auth_header=auth_header)
    if overview is None:
        logger.info("[ENV STATE] IoT overview unavailable for zone_id=%s", zone_id)
        if all_zone_ids and len(all_zone_ids) > 1:
            return _fetch_and_build_with_fallback(client, zone_context, all_zone_ids, auth_header)
        return {"env_state": None}

    env_state = _map_overview_to_env_state(zone_context, overview)
    if env_state is None:
        if all_zone_ids and len(all_zone_ids) > 1:
            return _fetch_and_build_with_fallback(client, zone_context, all_zone_ids, auth_header)
        return {"env_state": None}

    logger.debug(
        "[ENV STATE] source=%s zone=%s soil_pH=%s moisture=%s air_temp=%s humidity=%s",
        env_state.get("data_source"),
        zone_id,
        env_state["soil"].get("ph"),
        env_state["soil"].get("moisture_pct"),
        env_state["weather"].get("air_temp_c"),
        env_state["weather"].get("humidity_pct"),
    )
    return {"env_state": env_state}


def _fetch_and_build_with_fallback(
    client: "EnvGatewayClient",
    primary_context: Dict[str, Any],
    all_zone_ids: List[str],
    auth_header: str,
) -> dict:
    """Try each zone in order until one yields a non-empty IoT overview."""
    tried: List[str] = []
    for zone_id in all_zone_ids:
        if zone_id == primary_context["zone_id"]:
            continue
        tried.append(zone_id)
        zone_context = client.resolve_zone_context_from_zone(
            farm_zone_id=zone_id,
            auth_header=auth_header,
            farm_plot_id=primary_context.get("farm_plot_id"),
            plant_id=primary_context.get("plant_id"),
        )
        overview = client.get_zone_overview(zone_id=zone_id, auth_header=auth_header)
        if overview is None:
            logger.info("[ENV STATE] Fallback zone_id=%s has no IoT overview", zone_id)
            continue
        env_state = _map_overview_to_env_state(zone_context, overview)
        if env_state is not None:
            logger.info(
                "[ENV STATE] Fallback succeeded: used zone_id=%s (tried: %s)",
                zone_id,
                tried,
            )
            return {"env_state": env_state}
        logger.info("[ENV STATE] Fallback zone_id=%s produced empty readings", zone_id)

    logger.info(
        "[ENV STATE] All zone fallbacks exhausted (tried: %s); returning None",
        tried,
    )
    return {"env_state": None}
