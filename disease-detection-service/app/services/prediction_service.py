import io
import time
import logging
import requests
import numpy as np
from PIL import Image
from fastapi import UploadFile

from app.config.config import config
from app.config.security import UserPrincipal
from app.inference.ai_model_inference import AIModelInference
from app.dto.prediction.prediction_dto import PredictionResponse, PredictionResult
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode
from app.models.diagnose_request import DiagnoseRequest
from app.models.diagnose_result import DiagnoseResult, DiseaseResult
from app.repositories.diagnose_repository import DiagnoseRepository

logger = logging.getLogger(__name__)


class PredictionService:
    @staticmethod
    def preprocess_image(image_bytes: bytes) -> np.ndarray:
        try:
            image = Image.open(io.BytesIO(image_bytes))
            if image.mode != 'RGB':
                image = image.convert('RGB')
            image = image.resize(config.MODEL_INPUT_SIZE, Image.Resampling.BILINEAR)
            img_array = np.array(image, dtype=np.float32)
            img_array = np.expand_dims(img_array, axis=0)
            return img_array
        except Exception as e:
            logger.error(f"Image preprocessing failed: {e}")
            raise AppException(ErrorCode.BAD_REQUEST, f"Invalid image format: {str(e)}")

    @staticmethod
    def validate_file(file: UploadFile) -> bytes:
        if not file.content_type or not file.content_type.startswith('image/'):
             raise AppException(ErrorCode.INVALID_FILE_TYPE)

        image_bytes = file.file.read()
        if len(image_bytes) == 0:
             raise AppException(ErrorCode.EMPTY_FILE)
        return image_bytes

    # ------------------------------------------------------------------ #
    #  Internal helpers                                                    #
    # ------------------------------------------------------------------ #

    @staticmethod
    def _upload_to_file_service(file: UploadFile, image_bytes: bytes) -> str:
        """Uploads the image to file-service and returns the fileId. Returns empty string on failure."""
        url = f"{config.FILE_SERVICE_URL}/upload"
        files = {
            'file': (file.filename or "image.jpg", image_bytes, file.content_type or "image/jpeg")
        }
        try:
            response = requests.post(url, files=files, timeout=10)
            if response.status_code in [200, 201]:
                resp_json = response.json()
                return resp_json.get("data", {}).get("id", "")
            else:
                logger.error(f"Failed to upload to file-service. Status: {response.status_code}, Body: {response.text}")
        except Exception as e:
            logger.error(f"Exception calling file-service: {e}")
        return ""

    @staticmethod
    def _save_request(file: UploadFile, user_id: str, file_id: str = "", plant_id: str = "") -> str:
        """Persist DiagnoseRequest to MongoDB; returns diagnoseRequestId."""
        try:
            doc = DiagnoseRequest(
                userId=user_id,
                imageFileName=file.filename or "unknown",
                imageContentType=file.content_type or "image/unknown",
                fileId=file_id,
                plantId=plant_id or ""
            )
            return DiagnoseRepository.save_request(doc.to_dict())
        except Exception as e:
            logger.error(f"Could not save DiagnoseRequest: {e}")
            return ""

    @staticmethod
    def _save_result(diagnose_request_id: str, user_id: str, predictions: list) -> None:
        """Persist DiagnoseResult to MongoDB (all top-K predictions)."""
        try:
            disease_results = [
                DiseaseResult(
                    diseaseName=p.get("className", "unknown"),
                    confidenceScore=p.get("confidenceScore", 0.0),
                )
                for p in predictions
            ]
            doc = DiagnoseResult(
                diagnoseRequestId=diagnose_request_id,
                userId=user_id,
                result=disease_results,
            )
            DiagnoseRepository.save_result(doc.to_dict())
        except Exception as e:
            logger.error(f"Could not save DiagnoseResult: {e}")

    # ------------------------------------------------------------------ #
    #  Public prediction methods                                           #
    # ------------------------------------------------------------------ #

    @classmethod
    def predict(cls, file: UploadFile, model, current_user: UserPrincipal, plant_id: str | None = None) -> dict:
        start_time = time.time()

        try:
             image_bytes = cls.validate_file(file)
             image_array = cls.preprocess_image(image_bytes)

             if not model:
                  raise AppException(ErrorCode.MODEL_NOT_LOADED)

             # Upload to file-service
             file_id = cls._upload_to_file_service(file, image_bytes)

             # Persist request before inference
             diagnose_request_id = cls._save_request(file, current_user.id, file_id, plant_id or "")

             predictions = AIModelInference.perform_inference(model, image_array)
             processing_time = (time.time() - start_time) * 1000

             # Persist result after inference
             cls._save_result(diagnose_request_id, current_user.id, predictions)

             prediction_results = [PredictionResult(**p) for p in predictions]

             response = PredictionResponse(
                  predictions=prediction_results,
                  modelName=config.MODEL_NAME,
                  processingTimeMs=round(processing_time, 2)
             )
             return response.model_dump()
        finally:
             file.file.close()

    @classmethod
    def predict_internal(cls, file: UploadFile, model) -> dict:
        start_time = time.time()

        try:
             image_bytes = cls.validate_file(file)
             image_array = cls.preprocess_image(image_bytes)

             if not model:
                  raise AppException(ErrorCode.MODEL_NOT_LOADED)

             predictions = AIModelInference.perform_inference(model, image_array)
             processing_time = (time.time() - start_time) * 1000
             prediction_results = [PredictionResult(**p) for p in predictions]

             response = PredictionResponse(
                  predictions=prediction_results,
                  modelName=config.MODEL_NAME,
                  processingTimeMs=round(processing_time, 2)
             )
             return response.model_dump()
        finally:
             file.file.close()

