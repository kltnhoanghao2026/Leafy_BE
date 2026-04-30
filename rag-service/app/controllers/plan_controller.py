import logging
from typing import List, Optional

from fastapi import APIRouter, Depends, Query, Request

from app.core.security import get_current_user, require_roles, UserPrincipal
from app.dto.response.api_response import ApiResponse
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode
from app.i18n import get_message, resolve_locale
from app.repositories.plan_repository import get_plan_repository

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get(
    "/",
    response_model=ApiResponse[List[dict]],
    summary="List treatment plans",
    responses={
        200: {"description": "Paginated list of treatment plans."},
        401: {"description": "Authentication required."},
    },
)
async def list_plans(
    request: Request,
    page: int = Query(0, ge=0, description="Zero-based page number."),
    size: int = Query(20, ge=1, le=100, description="Number of plans per page."),
    current_user: UserPrincipal = Depends(get_current_user),
):
    """
    List treatment plans.
    - **ADMIN** users see all plans.
    - **USER** role sees only their own plans.
    """
    repo = get_plan_repository()
    skip = page * size

    is_admin = "ROLE_ADMIN" in current_user.roles
    if is_admin:
        plans = repo.find_all(skip=skip, limit=size)
    else:
        plans = repo.find_by_user(user_id=current_user.id, skip=skip, limit=size)

    locale = resolve_locale(request)
    return ApiResponse.success(result=plans, locale=locale)


@router.get(
    "/{plan_id}",
    response_model=ApiResponse[dict],
    summary="Get a single treatment plan",
    responses={
        200: {"description": "Treatment plan found."},
        404: {"description": "Plan not found."},
        403: {"description": "Access denied."},
    },
)
async def get_plan(
    request: Request,
    plan_id: str,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """Retrieve a single Plan by its UUID. Owner or ADMIN only."""
    repo = get_plan_repository()
    plan = repo.find_by_id(plan_id)

    if not plan:
        raise AppException(ErrorCode.PLAN_NOT_FOUND)

    is_admin = "ROLE_ADMIN" in current_user.roles
    if not is_admin and plan.get("userId") != current_user.id:
        raise AppException(ErrorCode.PLAN_ACCESS_DENIED)

    locale = resolve_locale(request)
    return ApiResponse.success(result=plan, locale=locale)


@router.delete(
    "/{plan_id}",
    response_model=ApiResponse[None],
    summary="Delete a treatment plan",
    responses={
        200: {"description": "Plan deleted."},
        404: {"description": "Plan not found."},
        403: {"description": "Access denied."},
    },
)
async def delete_plan(
    request: Request,
    plan_id: str,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """Hard-delete a Plan. Owner or ADMIN only."""
    repo = get_plan_repository()
    plan = repo.find_by_id(plan_id)

    if not plan:
        raise AppException(ErrorCode.PLAN_NOT_FOUND)

    is_admin = "ROLE_ADMIN" in current_user.roles
    if not is_admin and plan.get("userId") != current_user.id:
        raise AppException(ErrorCode.PLAN_ACCESS_DENIED)

    repo.delete_by_id(plan_id)
    logger.info("Plan deleted — planId=%s, by userId=%s", plan_id, current_user.id)
    locale = resolve_locale(request)
    return ApiResponse.success(
        message=get_message("response.treatment.plan.deleted", locale),
        locale=locale,
    )
