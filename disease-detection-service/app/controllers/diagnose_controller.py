"""
DiagnoseController — CRUD endpoints for DiagnoseRequest and DiagnoseResult.
Prefix: /diseases/diagnose
"""
import logging
from fastapi import APIRouter, Depends, Query

from app.config.security import get_current_user, UserPrincipal
from app.dto.response.api_response import ApiResponse
from app.services.diagnose_service import DiagnoseService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/diagnose", tags=["Diagnose History"])


# ------------------------------------------------------------------ #
#  DiagnoseRequest endpoints                                           #
# ------------------------------------------------------------------ #

@router.get("/requests", response_model=ApiResponse)
def get_all_requests(
    page: int = Query(default=0, ge=0, description="Page number (0-indexed)"),
    size: int = Query(default=20, ge=1, le=100, description="Page size"),
    current_user: UserPrincipal = Depends(get_current_user),
):
    """
    List diagnose requests with pagination.
    - ADMIN: sees all requests
    - USER: sees only their own requests
    """
    logger.info(f"GET /diagnose/requests - user={current_user.id}, page={page}, size={size}")
    result = DiagnoseService.get_all_requests(current_user, page=page, size=size)
    return ApiResponse.success(result)


@router.get("/requests/{diagnose_request_id}", response_model=ApiResponse)
def get_request_by_id(
    diagnose_request_id: str,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """
    Get a single diagnose request by ID.
    Only the owner or ADMIN may access it.
    """
    logger.info(f"GET /diagnose/requests/{diagnose_request_id} - user={current_user.id}")
    result = DiagnoseService.get_request(diagnose_request_id, current_user)
    return ApiResponse.success(result.model_dump())


@router.delete("/requests/{diagnose_request_id}", response_model=ApiResponse)
def delete_request(
    diagnose_request_id: str,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """
    Delete a diagnose request and its linked result.
    Only the owner or ADMIN may delete.
    """
    logger.info(f"DELETE /diagnose/requests/{diagnose_request_id} - user={current_user.id}")
    DiagnoseService.delete_request(diagnose_request_id, current_user)
    return ApiResponse.success(None)


from pydantic import BaseModel

class UpdatePlantRequest(BaseModel):
    plantId: str | None

@router.put("/requests/{diagnose_request_id}/plant", response_model=ApiResponse)
def update_request_plant(
    diagnose_request_id: str,
    payload: UpdatePlantRequest,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """
    Update the plantId for a specific diagnose request.
    Only the owner or ADMIN may update it.
    """
    logger.info(f"PUT /diagnose/requests/{diagnose_request_id}/plant - user={current_user.id}")
    plant_id = payload.plantId or ""
    DiagnoseService.update_request_plant(diagnose_request_id, plant_id, current_user)
    return ApiResponse.success(None)


# ------------------------------------------------------------------ #
#  DiagnoseResult endpoints                                            #
# ------------------------------------------------------------------ #

@router.get("/results", response_model=ApiResponse)
def get_all_results(
    page: int = Query(default=0, ge=0, description="Page number (0-indexed)"),
    size: int = Query(default=20, ge=1, le=100, description="Page size"),
    current_user: UserPrincipal = Depends(get_current_user),
):
    """
    List diagnose results with pagination.
    - ADMIN: sees all results
    - USER: sees only their own results
    """
    logger.info(f"GET /diagnose/results - user={current_user.id}, page={page}, size={size}")
    result = DiagnoseService.get_all_results(current_user, page=page, size=size)
    return ApiResponse.success(result)


@router.get("/results/by-request/{diagnose_request_id}", response_model=ApiResponse)
def get_result_by_request(
    diagnose_request_id: str,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """
    Get the diagnose result linked to a specific request.
    Only the owner or ADMIN may access it.
    """
    logger.info(f"GET /diagnose/results/by-request/{diagnose_request_id} - user={current_user.id}")
    result = DiagnoseService.get_result_by_request(diagnose_request_id, current_user)
    return ApiResponse.success(result.model_dump())
