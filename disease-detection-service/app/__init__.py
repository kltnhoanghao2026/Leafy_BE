"""
FastAPI Application Initialization
Production-grade Image Classification Service
"""
from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.controllers import router as api_router
from app.controllers.internal_prediction_controller import router as internal_prediction_router
from app.config.config import config
from app.exceptions.app_exception import AppException
from app.exceptions.global_exception_handler import (
    app_exception_handler,
    http_exception_handler,
    validation_exception_handler,
    general_exception_handler
)
from app.functions.middleware import add_process_time_header, lifespan

# Initialize FastAPI application with lifespan
app = FastAPI(
    title=config.SERVICE_NAME,
    description="Production-grade MobileNetV2 classification service for Spring Boot integration",
    version=config.SERVICE_VERSION,
    lifespan=lifespan
)

# Add middleware
app.middleware("http")(add_process_time_header)

# Add exception handlers
app.exception_handler(AppException)(app_exception_handler)
app.exception_handler(StarletteHTTPException)(http_exception_handler)
app.exception_handler(RequestValidationError)(validation_exception_handler)
app.exception_handler(Exception)(general_exception_handler)

# Include API routers
app.include_router(api_router)
app.include_router(internal_prediction_router)


@app.get("/")
def root():
    """Root endpoint with service information"""
    return {
        "service": config.SERVICE_NAME,
        "version": config.SERVICE_VERSION,
        "model": config.MODEL_NAME,
        "environment": config.ENV_STATE,
        "endpoints": {
            "predict": "/diseases/predict",
            "health": "/diseases/predict/health",
            "detectLeaf": "/diseases/detect-leaf",
            "detectLeafVisualize": "/diseases/detect-leaf/visualize",
            "detectLeafCrop": "/diseases/detect-leaf/crop",
            "leafDetectionHealth": "/diseases/detect-leaf/health",
            "docs": "/docs"
        }
    }


@app.get("/health")
def health():
    """Alternative health check endpoint at root level"""
    if not hasattr(app.state, 'model') or app.state.model is None:
        return {"status": "DOWN"}
    return {"status": "UP"}
