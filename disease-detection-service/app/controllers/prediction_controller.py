import logging
from fastapi import APIRouter, File, UploadFile, Request, Depends

from app.dto.response.api_response import ApiResponse
from app.services.prediction_service import PredictionService
from app.dto.prediction.prediction_dto import HealthResponse
from app.config.security import get_current_user, UserPrincipal

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/predict", tags=["Prediction"])

@router.post("", response_model=ApiResponse)
def predict(
    request: Request,
    file: UploadFile = File(...),
    current_user: UserPrincipal = Depends(get_current_user),
):
    """Classification endpoint for image prediction"""
    model = request.app.state.model
    prediction_response = PredictionService.predict(file, model, current_user)
    return ApiResponse.success(prediction_response)

@router.post("/tflite", response_model=ApiResponse)
def predict_tflite(
    request: Request,
    file: UploadFile = File(...),
    current_user: UserPrincipal = Depends(get_current_user),
):
    """Classification endpoint using TFLite model"""
    tflite_interpreter = request.app.state.tflite_interpreter
    prediction_response = PredictionService.predict_tflite(file, tflite_interpreter, current_user)
    return ApiResponse.success(prediction_response)

@router.get("/health", response_model=HealthResponse)
def health_check(request: Request):
    """Health check endpoint for prediction service"""
    from app.exceptions.app_exception import AppException
    from app.exceptions.error_code import ErrorCode
    if not hasattr(request.app.state, 'model') or request.app.state.model is None:
        raise AppException(ErrorCode.MODEL_NOT_LOADED)
    return HealthResponse(status="UP")
