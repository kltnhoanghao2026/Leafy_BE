from datetime import datetime
from typing import List
from pydantic import BaseModel, Field


class DiseaseResultItem(BaseModel):
    """Single disease prediction within a DiagnoseResult."""
    diseaseName: str = Field(..., description="Predicted disease class name")
    confidenceScore: float = Field(..., ge=0.0, le=1.0, description="Confidence score (0–1)")


class DiagnoseResultResponse(BaseModel):
    """DTO returned by the API for a DiagnoseResult document."""
    diagnoseResultId: str = Field(..., description="Unique result ID (UUID)")
    diagnoseRequestId: str = Field(..., description="Linked DiagnoseRequest ID")
    userId: str = Field(..., description="ID of the user who submitted the request")
    result: List[DiseaseResultItem] = Field(..., description="All top-K disease predictions")
    timeStamp: datetime = Field(..., description="UTC timestamp of the result")

    @classmethod
    def from_doc(cls, doc: dict) -> "DiagnoseResultResponse":
        return cls(
            diagnoseResultId=doc["diagnoseResultId"],
            diagnoseRequestId=doc["diagnoseRequestId"],
            userId=doc["userId"],
            result=[DiseaseResultItem(**r) for r in doc.get("result", [])],
            timeStamp=doc["timeStamp"],
        )
