from datetime import datetime
from pydantic import BaseModel, Field


class DiagnoseRequestResponse(BaseModel):
    """DTO returned by the API for a DiagnoseRequest document."""
    diagnoseRequestId: str = Field(..., description="Unique request ID (UUID)")
    userId: str = Field(..., description="ID of the user who submitted the request")
    imageFileName: str = Field(..., description="Original filename of the uploaded image")
    imageContentType: str = Field(..., description="MIME type of the uploaded image")
    timeStamp: datetime = Field(..., description="UTC timestamp of the request")

    @classmethod
    def from_doc(cls, doc: dict) -> "DiagnoseRequestResponse":
        return cls(
            diagnoseRequestId=doc["diagnoseRequestId"],
            userId=doc["userId"],
            imageFileName=doc["imageFileName"],
            imageContentType=doc["imageContentType"],
            timeStamp=doc["timeStamp"],
        )
