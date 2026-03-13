from typing import ClassVar
from pydantic import BaseModel, ConfigDict, Field

class PredictionResult(BaseModel):
    """Single prediction result with camelCase"""
    className: str = Field(..., description="ImageNet class name")
    confidenceScore: float = Field(
        ..., 
        ge=0.0, 
        le=1.0, 
        description="Confidence score between 0 and 1"
    )

class PredictionResponse(BaseModel):
    """Response model for predictions with camelCase"""
    model_config: ClassVar[ConfigDict] = ConfigDict(
        json_schema_extra={
            "example": {
                "predictions": [
                    {"className": "golden_retriever", "confidenceScore": 0.89},
                    {"className": "Labrador_retriever", "confidenceScore": 0.07}
                ],
                "modelName": "MobileNetV2",
                "processingTimeMs": 145.32
            }
        }
    )
    
    predictions: list[PredictionResult] = Field(..., description="Top predictions")
    modelName: str = Field(default="MobileNetV2", description="Model identifier")
    processingTimeMs: float | None = Field(None, description="Processing time in milliseconds")

class HealthResponse(BaseModel):
    """Health check response matching Spring Boot Actuator"""
    status: str = Field(default="UP", description="Service health status")
