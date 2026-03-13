from pydantic import BaseModel, Field
from typing import Optional, Dict, Any
from datetime import datetime
from enum import Enum


class TaskStatus(str, Enum):
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"


class TaskSchema(BaseModel):
    task_id: str = Field(..., description="Unique identifier for the background processing task.")
    status: TaskStatus = Field(..., description="Current lifecycle state of the task.")
    created_at: datetime = Field(..., description="UTC timestamp when the task was created.")
    updated_at: datetime = Field(..., description="UTC timestamp of the last status update.")
    message: Optional[str] = Field(None, description="Human-readable status message.")
    file_info: Optional[Dict[str, Any]] = Field(None, description="Metadata from the uploaded file.")
    error: Optional[str] = Field(None, description="Error message if task failed.")


class IngestResponse(BaseModel):
    task_id: str = Field(..., description="UUID of the background processing task.")
    status: str = Field(..., description="`accepted` or `skipped`.")
    message: str = Field(..., description="Human-readable summary of the ingestion outcome.")
    file_id: Optional[str] = Field(None, description="SHA-256 content hash of the uploaded file.")
