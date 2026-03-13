from typing import ClassVar
from pydantic import BaseModel, ConfigDict, Field

class BoundingBox(BaseModel):
    """Bounding box coordinates"""
    x1: float = Field(..., description="Top-left x coordinate")
    y1: float = Field(..., description="Top-left y coordinate")
    x2: float = Field(..., description="Bottom-right x coordinate")
    y2: float = Field(..., description="Bottom-right y coordinate")

class Detection(BaseModel):
    """Single detection result with camelCase"""
    className: str = Field(..., description="Detected class name")
    confidenceScore: float = Field(
        ..., 
        ge=0.0, 
        le=1.0, 
        description="Confidence score between 0 and 1"
    )
    boundingBox: BoundingBox = Field(..., description="Detection bounding box")

class LeafDetectionResponse(BaseModel):
    """Response model for leaf detection with camelCase"""
    model_config: ClassVar[ConfigDict] = ConfigDict(
        json_schema_extra={
            "example": {
                "detections": [
                    {
                        "className": "healthy_leaf",
                        "confidenceScore": 0.92,
                        "boundingBox": {
                            "x1": 100.5,
                            "y1": 150.2,
                            "x2": 250.8,
                            "y2": 300.1
                        }
                    }
                ],
                "modelName": "YOLOv8",
                "imageWidth": 640,
                "imageHeight": 480,
                "processingTimeMs": 85.42,
                "detectionCount": 1
            }
        }
    )
    
    detections: list[Detection] = Field(..., description="Detected leaves")
    modelName: str = Field(default="YOLOv8", description="Model identifier")
    imageWidth: int = Field(..., description="Original image width")
    imageHeight: int = Field(..., description="Original image height")
    processingTimeMs: float | None = Field(None, description="Processing time in milliseconds")
    detectionCount: int = Field(..., description="Number of detections")

class HealthResponse(BaseModel):
    """Health check response matching Spring Boot Actuator"""
    status: str = Field(default="UP", description="Service health status")
    modelsLoaded: dict[str, bool] = Field(
        default_factory=dict,
        description="Status of each model"
    )
