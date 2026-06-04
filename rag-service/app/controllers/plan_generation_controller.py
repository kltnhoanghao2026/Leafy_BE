"""
Plan Generation Controller

Provides standalone plan generation endpoints. Plans are automatically persisted
to plant-management-service after generation (non-fatal on save failure).

Routes:
    POST /rag/v2/plans/generate          — synchronous
    POST /rag/v2/plans/generate/stream  — SSE streaming
"""

import json
import logging
from typing import Any, AsyncIterator, Dict

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from app.core.security import get_current_user, UserPrincipal
from app.dto.response.api_response import ApiResponse
from app.dto.plan.plan_generation_dto import (
    PlanGenerationRequest,
    PlanGenerationResponse,
    PlanMetadata,
    PlanSource,
)
from app.services.plan_generation_service import get_plan_generation_service
from app.services.plant_management_client import get_plant_management_client
from app.repositories.plan_repository import get_plan_repository
from app.i18n import resolve_locale

logger = logging.getLogger(__name__)

router = APIRouter()


def _build_response(
    result: Dict[str, Any],
    saved_plan_id: str | None,
    rag_plan_id: str | None,
) -> PlanGenerationResponse:
    """Map the raw service result dict + optional saved IDs to a typed response."""
    metadata_dict = result["metadata"]
    return PlanGenerationResponse(
        plan=result["plan"],
        documents=[
            PlanSource(
                title=d["title"],
                content=d["content"],
                url=d.get("url"),
                score=d["score"],
            )
            for d in result["documents"]
        ],
        web_sources=[
            PlanSource(
                title=w["title"],
                content=w["content"],
                url=w.get("url"),
                score=w["score"],
            )
            for w in result["web_sources"]
        ],
        metadata=PlanMetadata(**metadata_dict),
        saved_plan_id=saved_plan_id,
        rag_plan_id=rag_plan_id,
    )


@router.post(
    "/generate",
    response_model=ApiResponse[PlanGenerationResponse],
    summary="Generate and persist a treatment plan",
    responses={
        200: {"description": "Plan generated and (optionally) saved."},
        401: {"description": "Authentication required."},
    },
)
async def generate_treatment_plan(
    request: Request,
    payload: PlanGenerationRequest,
    current_user: UserPrincipal = Depends(get_current_user),
) -> ApiResponse[PlanGenerationResponse]:
    """
    Generate a complete treatment plan for a detected coffee disease and
    persist it to plant-management-service.

    On success the response includes ``saved_plan_id`` — the plant-management-service
    document ID that can be used to navigate to the plan detail page.

    Saving is non-fatal: if plant-management-service is unreachable the plan is
    still returned with ``saved_plan_id: null`` so the caller can decide how
    to proceed.

    Subgraph flow:
      env_state → hybrid_search → reranker
        ↓
      check_doc_quality
        ├─ sufficient (best score ≥ 0.7) → planner
        └─ insufficient                → web_search_plan → planner
                                                     ↓
                                               safety_audit
                                                 ↓
                                      [check_safety_compliance]
                                        ├─ safe  → END
                                        └─ unsafe → refine
                                                        ↓
                                               [check_refinement_limit]
                                               ├─ retry_plan_search → web_search_plan (loop)
                                               └─ complete         → END (fallback)
    """
    auth_header = request.headers.get("Authorization")

    service = get_plan_generation_service()
    result = await service.generate_plan(
        disease_name=payload.disease_name,
        severity_level=payload.severity_level,
        plant_id=payload.plant_id,
        farm_plot_id=payload.farm_plot_id,
        farm_zone_id=payload.farm_zone_id,
        language=payload.language or "Vietnamese",
        image_url=payload.image_url,
        include_web_search=payload.include_web_search,
        user_id=current_user.id,
        auth_header=auth_header,
    )

    # ── Persist to plant-management-service (non-fatal) ────────────────────────
    saved_plan_id: str | None = None
    if auth_header and result.get("plan"):
        try:
            logger.info(
                "[PLAN CTRL] Syncing to plant-management-service | docs=%d | web_sources=%d | plan_source=%s",
                len(result.get("documents", [])),
                len(result.get("web_sources", [])),
                result["plan"].get("source"),
            )
            if result.get("documents"):
                for i, doc in enumerate(result["documents"]):
                    logger.debug("[PLAN CTRL]   doc[%d] title=%r", i, doc.get("title"))
            if result.get("web_sources"):
                for i, ws in enumerate(result["web_sources"]):
                    logger.debug("[PLAN CTRL]   web[%d] title=%r", i, ws.get("title"))
            client = get_plant_management_client()
            saved_plan_id = client.create_plan(
                generated_plan=result["plan"],
                plan_source=result["plan"].get("source"),
                source_documents=result.get("documents"),
                web_search_results=result.get("web_sources"),
                auth_header=auth_header,
            )
        except Exception as exc:
            logger.warning(
                "[PLAN CTRL] Failed to save plan to plant-management-service (non-fatal): %s",
                exc,
            )

    # ── Persist to rag-service MongoDB (always, non-fatal) ──────────────────
    rag_plan_id: str | None = None
    if result.get("plan") and current_user.id:
        try:
            repo = get_plan_repository()
            rag_plan_id = repo.save_rag_plan(
                generated_plan=result["plan"],
                source_documents=result.get("documents"),
                web_search_results=result.get("web_sources"),
                user_id=current_user.id,
                plant_management_plan_id=saved_plan_id,
            )
        except Exception as exc:
            logger.warning(
                "[PLAN CTRL] Failed to save plan to rag-service MongoDB (non-fatal): %s",
                exc,
            )

    locale = resolve_locale(request)
    response = _build_response(result, saved_plan_id, rag_plan_id)
    return ApiResponse.success(data=response, locale=locale)


async def _stream_plan_events(
    request: Request,
    payload: PlanGenerationRequest,
    current_user: UserPrincipal,
) -> AsyncIterator[bytes]:
    """
    Async generator of SSE event bytes.
    Extracted as a named function (instead of inlined in the route handler)
    so that the route handler stays clean and testable.
    """
    def _emit(event_name: str, data: dict) -> bytes:
        payload_str = json.dumps(data, ensure_ascii=False, default=str)
        return f"event: {event_name}\ndata: {payload_str}\n\n".encode()

    service = get_plan_generation_service()
    auth_header = request.headers.get("Authorization")

    # Acknowledge start
    yield _emit("start", {
        "disease_name": payload.disease_name,
        "language": payload.language or "Vietnamese",
        "include_web_search": payload.include_web_search,
    })

    try:
        result = await service.generate_plan(
            disease_name=payload.disease_name,
            severity_level=payload.severity_level,
            plant_id=payload.plant_id,
            farm_plot_id=payload.farm_plot_id,
            farm_zone_id=payload.farm_zone_id,
            language=payload.language or "Vietnamese",
            image_url=payload.image_url,
            include_web_search=payload.include_web_search,
            user_id=current_user.id,
            auth_header=auth_header,
        )
    except Exception as exc:
        logger.error("[PLAN STREAM] Generation failed: %s", exc)
        yield _emit("error", {"message": str(exc)})
        return

    # ── Persist to plant-management-service (non-fatal) ────────────────────────
    saved_plan_id: str | None = None
    if auth_header and result.get("plan"):
        try:
            logger.info(
                "[PLAN STREAM] Syncing to plant-management-service | docs=%d | web_sources=%d | plan_source=%s",
                len(result.get("documents", [])),
                len(result.get("web_sources", [])),
                result["plan"].get("source"),
            )
            if result.get("documents"):
                for i, doc in enumerate(result["documents"]):
                    logger.debug("[PLAN STREAM]   doc[%d] title=%r", i, doc.get("title"))
            if result.get("web_sources"):
                for i, ws in enumerate(result["web_sources"]):
                    logger.debug("[PLAN STREAM]   web[%d] title=%r", i, ws.get("title"))
            client = get_plant_management_client()
            saved_plan_id = client.create_plan(
                generated_plan=result["plan"],
                plan_source=result["plan"].get("source"),
                source_documents=result.get("documents"),
                web_search_results=result.get("web_sources"),
                auth_header=auth_header,
            )
        except Exception as exc:
            logger.warning(
                "[PLAN STREAM] Failed to save plan to plant-management-service (non-fatal): %s",
                exc,
            )

    # ── Persist to rag-service MongoDB (always, non-fatal) ──────────────────
    rag_plan_id: str | None = None
    if result.get("plan") and current_user.id:
        try:
            repo = get_plan_repository()
            rag_plan_id = repo.save_rag_plan(
                generated_plan=result["plan"],
                source_documents=result.get("documents"),
                web_search_results=result.get("web_sources"),
                user_id=current_user.id,
                plant_management_plan_id=saved_plan_id,
            )
        except Exception as exc:
            logger.warning(
                "[PLAN STREAM] Failed to save plan to rag-service MongoDB (non-fatal): %s",
                exc,
            )

    yield _emit("done", {
        "plan": result["plan"],
        "documents": result["documents"],
        "web_sources": result["web_sources"],
        "metadata": result["metadata"],
        "saved_plan_id": saved_plan_id,
        "rag_plan_id": rag_plan_id,
    })


@router.post(
    "/generate/stream",
    summary="Generate and persist a treatment plan with SSE streaming progress",
    responses={
        200: {
            "description": (
                "Server-Sent Events stream. "
                "First event: 'start' (config acknowledgement). "
                "Final event: 'done' (full plan payload including saved_plan_id). "
                "On error: 'error' event."
            ),
            "content": {"text/event-stream": {}},
        },
        401: {"description": "Authentication required."},
    },
)
async def generate_treatment_plan_stream(
    request: Request,
    payload: PlanGenerationRequest,
    current_user: UserPrincipal = Depends(get_current_user),
) -> StreamingResponse:
    """
    SSE variant of POST /generate.

    Useful for UIs that want to show live progress or handle partial results.
    The stream contains a 'start' acknowledgement, then a 'done' event
    with the full plan payload and ``saved_plan_id`` on success,
    or an 'error' event on failure.
    """
    return StreamingResponse(
        _stream_plan_events(request, payload, current_user),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
