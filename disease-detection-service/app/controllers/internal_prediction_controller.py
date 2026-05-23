import logging
from fastapi import APIRouter, File, Request, UploadFile

from app.dto.response.api_response import ApiResponse
from app.services.prediction_service import PredictionService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/internal/diseases", tags=["Internal Prediction"])


@router.post("/predict", response_model=ApiResponse)
def predict_internal(
    request: Request,
    file: UploadFile = File(...),
):
    """Internal service-to-service classification endpoint."""
    model = request.app.state.model
    prediction_response = PredictionService.predict_internal(file, model)
    return ApiResponse.success(prediction_response)
