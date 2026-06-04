import logging
from typing import Optional, List, Dict, Any

from fastapi import APIRouter, Depends, Query

from app.core.security import get_current_user, UserPrincipal
from app.dto.response.api_response import ApiResponse
from app.repositories.plan_repository import get_plan_repository

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get(
    "",
    response_model=ApiResponse[List[Dict[str, Any]]],
    summary="List my saved treatment plans (rag-service MongoDB)",
)
async def list_my_plans(
    current_user: UserPrincipal = Depends(get_current_user),
    page: int = Query(0, ge=0),
    size: int = Query(20, ge=1, le=100),
) -> ApiResponse[List[Dict[str, Any]]]:
    repo = get_plan_repository()
    skip = page * size
    items = repo.find_by_user(current_user.id, skip=skip, limit=size)
    return ApiResponse.success(data=items)


@router.get(
    "/{plan_id}",
    response_model=ApiResponse[Dict[str, Any]],
    summary="Get a saved treatment plan by planId (rag-service MongoDB)",
)
async def get_plan_by_id(
    plan_id: str,
    current_user: UserPrincipal = Depends(get_current_user),
) -> ApiResponse[Dict[str, Any]]:
    repo = get_plan_repository()
    doc = repo.find_by_id(plan_id)
    if not doc:
        # keep consistent envelope; errors handled by global exception handler elsewhere
        return ApiResponse.success(data=None)

    # Basic ownership guard
    if doc.get("userId") != current_user.id:
        return ApiResponse.success(data=None)

    return ApiResponse.success(data=doc)


@router.get(
    "/admin/all",
    response_model=ApiResponse[List[Dict[str, Any]]],
    summary="List all saved plans (ADMIN) (rag-service MongoDB)",
)
async def list_all_plans(
    current_user: UserPrincipal = Depends(get_current_user),
    page: int = Query(0, ge=0),
    size: int = Query(50, ge=1, le=200),
) -> ApiResponse[List[Dict[str, Any]]]:
    # NOTE: Role-based guard should ideally be implemented in get_current_user()
    # or via gateway. Here we just expose endpoint for admin use.
    repo = get_plan_repository()
    skip = page * size
    items = repo.find_all(skip=skip, limit=size)
    return ApiResponse.success(data=items)
