from typing import Optional
from pydantic import BaseModel, Field

class GeneratePlanRequest(BaseModel):
    disease_name: str = Field(
        ..., 
        description="The name of the detected disease.",
        examples=["Coffee Leaf Rust"]
    )
    plant_id: Optional[str] = Field(
        None, 
        description="Optional Plant ID associated with this disease.",
        examples=["plant123"]
    )
    farm_plot_id: Optional[str] = Field(
        None,
        description="Farm plot ID to use for IoT environmental context.",
        examples=["abc123def456abc123def456"],
    )
    farm_zone_id: Optional[str] = Field(
        None,
        description="Farm zone ID to use for IoT environmental context.",
        examples=["abc123def456abc123def456"],
    )
    language: Optional[str] = Field(
        "English", 
        description="The language for the generated plan.",
        examples=["Vietnamese", "English"]
    )
    image_url: Optional[str] = Field(
        None,
        description="Presigned URL of the disease image for severity assessment",
    )
