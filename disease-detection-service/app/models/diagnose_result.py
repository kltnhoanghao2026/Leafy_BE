"""
DiagnoseResult MongoDB document model
Stores the AI model's disease prediction results for a given DiagnoseRequest.
"""
from datetime import datetime, timezone
from dataclasses import dataclass, field
from typing import List
import uuid


@dataclass
class DiseaseResult:
    """Single disease prediction entry (diseaseName + confidenceScore)."""
    diseaseName: str
    confidenceScore: float

    def to_dict(self) -> dict:
        return {
            "diseaseName": self.diseaseName,
            "confidenceScore": self.confidenceScore,
        }


@dataclass
class DiagnoseResult:
    """
    Represents a disease diagnosis result document stored in MongoDB.

    Collection: diagnose_results
    """
    diagnoseRequestId: str
    userId: str
    result: List[DiseaseResult]
    diagnoseResultId: str = field(default_factory=lambda: str(uuid.uuid4()))
    timeStamp: datetime = field(default_factory=lambda: datetime.now(timezone.utc))

    def to_dict(self) -> dict:
        return {
            "diagnoseResultId": self.diagnoseResultId,
            "diagnoseRequestId": self.diagnoseRequestId,
            "userId": self.userId,
            "result": [r.to_dict() for r in self.result],
            "timeStamp": self.timeStamp,
        }
