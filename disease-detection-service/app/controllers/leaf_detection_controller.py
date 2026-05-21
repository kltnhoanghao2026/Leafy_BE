import logging
from fastapi import APIRouter, File, Query, Request, UploadFile
from fastapi.responses import Response

from app.dto.response.api_response import ApiResponse
from app.services.leaf_detection_service import LeafDetectionService
from app.dto.leaf_detection.leaf_detection_dto import HealthResponse
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/detect-leaf", tags=["Leaf Detection"])

def get_yolo_model(request: Request, model_type: str):
    if model_type == "custom":
        if not hasattr(request.app.state, 'yolo_custom_model') or request.app.state.yolo_custom_model is None:
            raise AppException(ErrorCode.MODEL_NOT_LOADED)
        class_names = getattr(request.app.state, 'yolo_custom_class_names', None)
        return request.app.state.yolo_custom_model, class_names, "YOLOv8-Custom"
    elif model_type == "base":
        if not hasattr(request.app.state, 'yolo_base_model') or request.app.state.yolo_base_model is None:
            raise AppException(ErrorCode.MODEL_NOT_LOADED)
        return request.app.state.yolo_base_model, None, "YOLOv8n"
    else:
        raise AppException(ErrorCode.INVALID_MODEL_TYPE)

@router.post("", response_model=ApiResponse)
def detect_leaf(
    request: Request,
    file: UploadFile = File(...),
    model_type: str = Query(default="custom", description="Model to use: 'custom' or 'base'"),
    confidence: float = Query(default=0.25, ge=0.0, le=1.0)
):
    model, class_names, model_name = get_yolo_model(request, model_type)
    leaf_detection_response = LeafDetectionService.detect_leaf(file, model, class_names, model_name, confidence)
    return ApiResponse.success(leaf_detection_response)

@router.get("/health", response_model=HealthResponse)
def health_check(request: Request):
    models_loaded = {
        "custom_model": hasattr(request.app.state, 'yolo_custom_model') and request.app.state.yolo_custom_model is not None,
        "base_model": hasattr(request.app.state, 'yolo_base_model') and request.app.state.yolo_base_model is not None
    }
    
    if not any(models_loaded.values()):
        raise AppException(ErrorCode.MODEL_NOT_LOADED)
    
    return HealthResponse(status="UP", modelsLoaded=models_loaded)

@router.post("/visualize", status_code=200)
def detect_and_visualize(
    request: Request,
    file: UploadFile = File(...),
    model_type: str = Query(default="custom"),
    confidence: float = Query(default=0.25, ge=0.0, le=1.0),
    box_color: str = Query(default="green"),
    box_thickness: int = Query(default=3, ge=1, le=10)
):
    model, class_names, _ = get_yolo_model(request, model_type)
    img_bytes, detection_count = LeafDetectionService.visualize(file, model, class_names, confidence, box_color, box_thickness)

    return Response(
        content=img_bytes,
        media_type="image/jpeg",
        headers={
            "X-Detection-Count": str(detection_count),
            "Content-Disposition": f"inline; filename=detection_result.jpg"
        }
    )

@router.post("/crop", status_code=200)
def detect_and_crop(
    request: Request,
    file: UploadFile = File(...),
    model_type: str = Query(default="custom"),
    confidence: float = Query(default=0.25, ge=0.0, le=1.0),
    padding: int = Query(default=10, ge=0, le=100),
    return_format: str = Query(default="zip")
):
    model, class_names, _ = get_yolo_model(request, model_type)
    content, media_type, filename, detection_count = LeafDetectionService.detect_and_crop(file, model, class_names, confidence, padding, return_format)

    return Response(
        content=content,
        media_type=media_type,
        headers={
            "X-Detection-Count": str(detection_count),
            "Content-Disposition": f'{"inline" if return_format.lower() == "single" else "attachment"}; filename="{filename}"'
        }
    )
