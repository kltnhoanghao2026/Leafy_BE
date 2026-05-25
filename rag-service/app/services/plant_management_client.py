"""HTTP client for creating treatment plans in the plant-management-service.

Forwards the caller's auth header so that the service can resolve the
authenticated user's profileId and set it as the plan owner.
"""
import logging
import time
from typing import Any, Dict, List, Optional

import httpx

from app.config.settings import settings

logger = logging.getLogger(__name__)

_TIMEOUT = httpx.Timeout(connect=10.0, read=30.0, write=30.0, pool=10.0)


class PlantManagementClient:
    """Singleton client for the plant-management-service plan API."""

    _instance = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    @property
    def _base_url(self) -> str:
        return settings.api_gateway_url.rstrip("/")

    def create_plan(
        self,
        *,
        generated_plan: Dict[str, Any],
        plan_source: Optional[str],
        source_documents: Optional[List[Dict[str, Any]]],
        web_search_results: Optional[List[Dict[str, Any]]],
        auth_header: str,
    ) -> Optional[str]:
        """POST /api/plans to plant-management-service.

        The schedule items are sent as ``EmbeddedPlanEventRequest`` objects
        and will be stored as an embedded array inside the Plan document
        (no separate PlantEvent collection documents are created at this stage).

        Returns the plant-management plan ID on success, or None on failure.
        Errors are logged but never propagated — plan creation in the RAG
        service's own MongoDB has already succeeded at this point.
        """
        payload = self._build_payload(
            generated_plan=generated_plan,
            plan_source=plan_source,
            source_documents=source_documents,
            web_search_results=web_search_results,
        )

        url = f"{self._base_url}/api/plans"
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "Authorization": auth_header,
        }

        logger.info(
            "[PLAN SYNC] Sending POST to %s\n  headers=%s\n  payload=%s",
            url,
            {k: v if k != "Authorization" else "***" for k, v in headers.items()},
            payload,
        )

        start = time.monotonic()
        try:
            with httpx.Client(timeout=_TIMEOUT) as client:
                response = client.post(url, json=payload, headers=headers)
            elapsed_ms = (time.monotonic() - start) * 1000
            logger.info("[PLAN SYNC] Response received in %.1fms — status=%d", elapsed_ms, response.status_code)

            if response.status_code >= 400:
                logger.warning(
                    "[PLAN SYNC] plant-management-service returned status=%d — body=%s",
                    response.status_code,
                    response.text[:200],
                )
                return None

            data = response.json()
            # Unwrap standard ApiResponse envelope: { code, data: { id, ... } }
            if isinstance(data, dict) and "data" in data:
                data = data["data"]
            plan_id = data.get("id") if isinstance(data, dict) else None
            logger.info(
                "[PLAN SYNC] Plan created in plant-management-service — id=%s",
                plan_id,
            )
            return plan_id

        except httpx.RequestError as exc:
            elapsed_ms = (time.monotonic() - start) * 1000
            logger.warning(
                "[PLAN SYNC] HTTP request failed after %.1fms — url=%s, timeout=%s, error=%s",
                elapsed_ms, url, _TIMEOUT, exc,
            )
            return None
        except Exception as exc:
            logger.warning("[PLAN SYNC] Unexpected error: %s", exc, exc_info=True)
            return None

    # ── Payload builder ────────────────────────────────────────────────────────

    def _build_payload(
        self,
        *,
        generated_plan: Dict[str, Any],
        plan_source: Optional[str],
        source_documents: Optional[List[Dict[str, Any]]] = None,
        web_search_results: Optional[List[Dict[str, Any]]] = None,
    ) -> Dict[str, Any]:
        # Strip empty {} entries from sourceDocuments — they indicate no
        # meaningful retrieval context was available at generation time.
        docs = [
            d for d in (source_documents or [])
            if d and isinstance(d, dict) and any(v for v in d.values() if v)
        ]
        web = web_search_results or []

        schedule = generated_plan.get("schedule")
        payload: Dict[str, Any] = {
            "planName": generated_plan.get("planName"),
            "source": plan_source,
            "sourceType": "RAG_GEN",
            "sourceDocuments": docs if docs else [],
            "webSearchResults": web,
            "plantId": generated_plan.get("plantId"),
            "farmPlotId": generated_plan.get("farmPlotId"),
            "farmZoneId": generated_plan.get("farmZoneId"),
            "diseaseName": generated_plan.get("diseaseName"),
            "confidenceScore": generated_plan.get("confidenceScore"),
            "severityLevel": generated_plan.get("severityLevel"),
            "requiredInputs": generated_plan.get("requiredInputs"),
            "safetyWarnings": generated_plan.get("safetyWarnings"),
            "successIndicators": generated_plan.get("successIndicators"),
            "estimatedCost": generated_plan.get("estimatedCost"),
            "schedule": self._normalise_schedule(schedule) if schedule else None,
            "isPublic": False,
        }
        # Strip explicit None values so we don't override server-side defaults
        return {k: v for k, v in payload.items() if v is not None}

    @staticmethod
    def _normalise_schedule(schedule: Any) -> Optional[List[Dict[str, Any]]]:
        """Normalise schedule items to plain dicts for JSON serialisation.

        Strips all runtime and scope fields that are not part of
        ``EmbeddedPlanEventRequest`` — the backend resolves these at apply time:

        * ``sourcePlanId`` / ``planApplyId`` — set by the service after persistence
        * ``isPlanned`` — determined by the service based on daysFromStart
        * ``farmPlotId`` / ``farmZoneId`` / ``plantId`` — injected from the apply request
        * ``calculatedStartDate`` / ``calculatedEndDate`` — computed at apply time
        """
        # Fields that must NOT reach EmbeddedPlanEventRequest
        _STRIP_FIELDS = {
            "sourcePlanId", "planApplyId",
            "isPlanned",
            "farmPlotId", "farmZoneId", "plantId",
            "calculatedStartDate", "calculatedEndDate",
        }
        if not isinstance(schedule, list):
            return None
        result = []
        for item in schedule:
            if hasattr(item, "model_dump"):
                d = item.model_dump(mode="json", exclude_none=True)
            elif isinstance(item, dict):
                d = dict(item)
            else:
                continue
            for field in _STRIP_FIELDS:
                d.pop(field, None)
            result.append(d)
        return result or None


_plant_management_client = PlantManagementClient()


def get_plant_management_client() -> PlantManagementClient:
    return _plant_management_client
