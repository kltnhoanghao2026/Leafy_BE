"""
DiagnoseRequest MongoDB document model
Stores metadata about an incoming disease diagnosis request (image upload).
"""
from datetime import datetime, timezone
from dataclasses import dataclass, field
import uuid


@dataclass
class DiagnoseRequest:
    """
    Represents a disease diagnosis request document stored in MongoDB.

    Collection: diagnose_requests
    """
    userId: str
    imageFileName: str
    imageContentType: str
    fileId: str = ""
    plantId: str = ""
    diagnoseRequestId: str = field(default_factory=lambda: str(uuid.uuid4()))
    timeStamp: datetime = field(default_factory=lambda: datetime.now(timezone.utc))

    def to_dict(self) -> dict:
        return {
            "diagnoseRequestId": self.diagnoseRequestId,
            "userId": self.userId,
            "imageFileName": self.imageFileName,
            "imageContentType": self.imageContentType,
            "fileId": self.fileId,
            "plantId": self.plantId,
            "timeStamp": self.timeStamp,
        }
