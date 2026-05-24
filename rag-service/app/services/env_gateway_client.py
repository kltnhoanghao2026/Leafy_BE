import logging
from typing import Any, Dict, List, Optional, Tuple

import httpx

from app.config.settings import settings

logger = logging.getLogger(__name__)


class EnvGatewayClient:
    """Gateway client for resolving plant -> plot -> zone and fetching IoT zone overview."""

    def __init__(self, base_url: Optional[str] = None, timeout_seconds: Optional[float] = None) -> None:
        self.base_url = (base_url or settings.api_gateway_url).rstrip("/")
        self.timeout_seconds = timeout_seconds or settings.env_lookup_timeout_seconds

    def resolve_zone_context(self, plant_id: str, auth_header: Optional[str]) -> Tuple[Optional[Dict[str, Any]], List[str]]:
        """Resolve a single deterministic zone context from a plant id.

        Returns (None, []) when the mapping is missing or ambiguous.
        """
        plant = self._get_wrapped_data(f"/api/plants/{plant_id}", auth_header)
        if not isinstance(plant, dict):
            logger.info("[ENV LOOKUP] Plant not found or unreadable for plant_id=%s", plant_id)
            return None, []

        farm_plot_id = plant.get("farmPlotId")
        if not farm_plot_id:
            logger.info("[ENV LOOKUP] Missing farmPlotId for plant_id=%s", plant_id)
            return None, []

        return self.resolve_zone_context_from_plot(
            farm_plot_id=str(farm_plot_id),
            auth_header=auth_header,
            plant_id=plant_id,
        )

    def resolve_zone_context_from_plot(
        self,
        farm_plot_id: str,
        auth_header: Optional[str],
        plant_id: Optional[str] = None,
    ) -> Tuple[Optional[Dict[str, Any]], List[str]]:
        """Resolve a single deterministic zone context directly from a farm plot id.

        Returns (None, []) when the zone list is unavailable or empty.
        Also fetches farm plot details and embeds them in the returned context.
        """
        zones_payload = self._get_wrapped_data(f"/api/farms/plots/{farm_plot_id}/zones", auth_header)
        if not isinstance(zones_payload, list):
            logger.info("[ENV LOOKUP] Zone list unavailable for farm_plot_id=%s", farm_plot_id)
            return None, []

        zones = [zone for zone in zones_payload if isinstance(zone, dict) and zone.get("id")]
        if not zones:
            logger.info("[ENV LOOKUP] No zones found for farm_plot_id=%s", farm_plot_id)
            return None, []

        all_zone_ids: List[str] = [str(zone.get("id")) for zone in zones]

        if len(zones) > 1:
            logger.info(
                "[ENV LOOKUP] Multiple zones (%d) for farm_plot_id=%s — using first zone",
                len(zones),
                farm_plot_id,
            )

        zone = zones[0]
        plot_info = self.get_farm_plot_info(farm_plot_id=farm_plot_id, auth_header=auth_header)
        zone_context = {
            "plant_id": plant_id,
            "farm_plot_id": str(farm_plot_id),
            "zone_id": str(zone.get("id")),
            "altitude_m": self._to_float(zone.get("elevationM")),
            # Zone metadata (already available from zones list payload)
            "zone_name": zone.get("zoneName"),
            "zone_code": zone.get("zoneCode"),
            "soil_type": zone.get("soilType"),
            "crop_type": zone.get("cropType"),
            "zone_area_m2": self._to_float(zone.get("areaM2")),
            # Farm plot metadata
            "plot_name": plot_info.get("name") if plot_info else None,
            "plot_code": plot_info.get("code") if plot_info else None,
            "plot_area_m2": self._to_float(plot_info.get("areaM2")) if plot_info else None,
            "plot_address": plot_info.get("addressLine") if plot_info else None,
            "plot_latitude": self._to_float(plot_info.get("latitude")) if plot_info else None,
            "plot_longitude": self._to_float(plot_info.get("longitude")) if plot_info else None,
        }
        return zone_context, all_zone_ids

    def resolve_zone_context_from_zone(
        self,
        farm_zone_id: str,
        auth_header: Optional[str],
        farm_plot_id: Optional[str] = None,
        plant_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Build a zone context from a known zone id by fetching zone and plot details."""
        zone_info = self.get_farm_zone_info(farm_zone_id=farm_zone_id, auth_header=auth_header)
        resolved_plot_id = farm_plot_id or (zone_info.get("farmPlotId") if zone_info else None)
        plot_info = self.get_farm_plot_info(farm_plot_id=resolved_plot_id, auth_header=auth_header) if resolved_plot_id else None
        return {
            "plant_id": plant_id,
            "farm_plot_id": resolved_plot_id,
            "zone_id": str(farm_zone_id),
            "altitude_m": self._to_float(zone_info.get("elevationM")) if zone_info else None,
            # Zone metadata
            "zone_name": zone_info.get("zoneName") if zone_info else None,
            "zone_code": zone_info.get("zoneCode") if zone_info else None,
            "soil_type": zone_info.get("soilType") if zone_info else None,
            "crop_type": zone_info.get("cropType") if zone_info else None,
            "zone_area_m2": self._to_float(zone_info.get("areaM2")) if zone_info else None,
            # Farm plot metadata
            "plot_name": plot_info.get("name") if plot_info else None,
            "plot_code": plot_info.get("code") if plot_info else None,
            "plot_area_m2": self._to_float(plot_info.get("areaM2")) if plot_info else None,
            "plot_address": plot_info.get("addressLine") if plot_info else None,
            "plot_latitude": self._to_float(plot_info.get("latitude")) if plot_info else None,
            "plot_longitude": self._to_float(plot_info.get("longitude")) if plot_info else None,
        }

    def get_farm_plot_info(self, farm_plot_id: str, auth_header: Optional[str]) -> Optional[Dict[str, Any]]:
        """Fetch farm plot details (name, area, location) from plant-management-service."""
        plot = self._get_wrapped_data(f"/api/farms/plots/{farm_plot_id}", auth_header)
        if not isinstance(plot, dict):
            logger.info("[ENV LOOKUP] Farm plot info unavailable for farm_plot_id=%s", farm_plot_id)
            return None
        return plot

    def get_farm_zone_info(self, farm_zone_id: str, auth_header: Optional[str]) -> Optional[Dict[str, Any]]:
        """Fetch farm zone details (name, soil type, crop type, area) from plant-management-service."""
        zone = self._get_wrapped_data(f"/api/farms/zones/{farm_zone_id}", auth_header)
        if not isinstance(zone, dict):
            logger.info("[ENV LOOKUP] Farm zone info unavailable for farm_zone_id=%s", farm_zone_id)
            return None
        return zone

    def get_zone_overview(self, zone_id: str, auth_header: Optional[str]) -> Optional[Dict[str, Any]]:
        """Fetch IoT zone overview for a resolved zone id."""
        payload = self._get_json(f"/api/iot/farm-zones/{zone_id}/overview", auth_header)
        if not isinstance(payload, dict):
            logger.info("[ENV LOOKUP] IoT zone overview unavailable for zone_id=%s", zone_id)
            return None

        # IoT responses are usually raw objects. If wrapped, unwrap data.
        if "data" in payload and "code" in payload:
            data = payload.get("data")
            return data if isinstance(data, dict) else None

        return payload

    def _get_wrapped_data(self, path: str, auth_header: Optional[str]) -> Optional[Any]:
        payload = self._get_json(path, auth_header)
        if not isinstance(payload, dict):
            return None

        if "data" not in payload:
            logger.warning("[ENV LOOKUP] Expected wrapped response for path=%s", path)
            return None

        return payload.get("data")

    def _get_json(self, path: str, auth_header: Optional[str]) -> Optional[Any]:
        url = f"{self.base_url}{path}"
        headers: Dict[str, str] = {"Accept": "application/json"}
        if auth_header:
            headers["Authorization"] = auth_header

        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                response = client.get(url, headers=headers)
        except httpx.RequestError as exc:
            logger.warning("[ENV LOOKUP] Request failed for %s: %s", url, exc)
            return None

        if response.status_code >= 400:
            logger.info("[ENV LOOKUP] Non-success status=%d for %s", response.status_code, url)
            return None

        try:
            return response.json()
        except ValueError:
            logger.warning("[ENV LOOKUP] Non-JSON response for %s", url)
            return None

    @staticmethod
    def _to_float(value: Any) -> Optional[float]:
        try:
            if value is None:
                return None
            return float(value)
        except (TypeError, ValueError):
            return None


_gateway_client = EnvGatewayClient()


def get_env_gateway_client() -> EnvGatewayClient:
    return _gateway_client
