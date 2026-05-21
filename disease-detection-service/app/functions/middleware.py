"""
Middleware and lifespan management for FastAPI
"""
import logging
import time
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request

from app.config.config import config
from app.functions.logger import setup_logger
from app.inference.ai_model_inference import AIModelInference

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Lifespan context manager for FastAPI
    Handles startup and shutdown events
    """
    # Startup
    setup_logger()
    logger.info(f"Starting {config.SERVICE_NAME} - ENV={config.ENV_STATE}")
    
    # Load and warmup classification model
    model = AIModelInference.load_ml_model()
    AIModelInference.warmup_ml_model(model)
    
    # Store model in app state
    app.state.model = model
    
    # Load and warmup YOLO models
    try:
        # Load custom YOLO model (best.pt)
        custom_model_path = "weights/yolo/best.pt"
        if Path(custom_model_path).exists():
            logger.info("Loading custom YOLO model (single_cls coffee leaf detector)...")
            yolo_class_names = ["coffee_leaf"]
            yolo_custom = AIModelInference.load_yolo_model(custom_model_path)
            app.state.yolo_custom_model = yolo_custom
            app.state.yolo_custom_class_names = yolo_class_names
            AIModelInference.warmup_yolo_model(yolo_custom)
            logger.info(f"Custom YOLO model loaded successfully")
        else:
            logger.warning(f"Custom YOLO model not found at {custom_model_path}")
            app.state.yolo_custom_model = None
            app.state.yolo_custom_class_names = None
    except Exception as e:
        logger.error(f"Failed to load custom YOLO model: {e}")
        app.state.yolo_custom_model = None
        app.state.yolo_custom_class_names = None
    
    # Base YOLO model - set to None since we're using ONNX only
    # If you have a base YOLO model in ONNX format, update the path below
    app.state.yolo_base_model = None
    logger.info("YOLO models loaded (base YOLO model skipped - ONNX only)")
    
    logger.info("Service startup complete - ready to handle requests")
    
    yield

    # Shutdown
    logger.info("Shutting down the application")
    from app.repositories.diagnose_repository import DiagnoseRepository
    DiagnoseRepository.close()


async def add_process_time_header(request: Request, call_next):
    """Middleware to add processing time to response headers"""
    start_time = time.time()
    response = await call_next(request)
    process_time = time.time() - start_time
    response.headers["X-Process-Time"] = f"{process_time:.4f}"
    return response
