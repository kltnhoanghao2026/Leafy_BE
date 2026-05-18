from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List
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


# ── Chunk Preview DTOs ───────────────────────────────────────────────────────


class ChunkPreview(BaseModel):
    """A single chunk from a document preview (dry-run parsing)."""

    index: int = Field(..., description="0-based position of this chunk in the document.")
    text: str = Field(..., description="Chunk text content.")
    section: Optional[str] = Field(None, description="Semantic section — not set for flat chunking.")
    element_type: Optional[str] = Field(None, description="Source element type — not set for flat chunking.")
    point_id: Optional[str] = Field(None, description="Qdrant point ID of this chunk.")


class PreviewResponse(BaseModel):
    """Result of a document preview (parse + chunk without persisting)."""

    filename: str = Field(..., description="Original filename of the uploaded document.")
    total_chunks: int = Field(..., description="Total number of chunks produced.")
    sections: List[str] = Field(default=[], description="Distinct sections detected in the document.")
    chunks: List[ChunkPreview] = Field(default=[], description="All chunk previews.")


# ── Document Catalog DTOs ────────────────────────────────────────────────────


class DocumentSummary(BaseModel):
    """Summary of an ingested document stored in the catalog."""

    document_id: str = Field(..., description="SHA-256 hash of the file (deduplication key).")
    original_filename: str = Field(..., description="Original filename at upload time.")
    content_type: Optional[str] = Field(None, description="MIME type of the source file.")
    file_size: Optional[int] = Field(None, description="File size in bytes.")
    category: Optional[str] = Field(None, description="Document category (agronomy, disease, regulation, etc.).")
    variety: Optional[str] = Field(None, description="Crop variety tag.")
    user_id: Optional[str] = Field(None, description="ID of the user who uploaded the document.")
    file_service_id: Optional[str] = Field(None, description="File ID in the file-service (S3 reference).")
    file_service_s3_key: Optional[str] = Field(None, description="S3 object key for file download.")
    chunk_count: int = Field(0, description="Number of chunks produced from this document.")
    sections: List[str] = Field(default=[], description="Semantic sections detected.")
    status: str = Field("ingested", description="Document status (ingested, failed).")
    ingested_at: Optional[datetime] = Field(None, description="Timestamp when the document was ingested.")


class DocumentDetail(DocumentSummary):
    """Full document detail including its chunks."""

    chunks: List[ChunkPreview] = Field(default=[], description="All chunks from this document.")


class ChunkDetail(BaseModel):
    """Full chunk detail returned by the chunks-by-point-ids endpoint."""

    chunk_id: str = Field(..., description="MongoDB chunk identifier (UUID).")
    document_id: str = Field(..., description="SHA-256 file hash of the source document.")
    chunk_index: int = Field(..., description="0-based position of this chunk in the source document.")
    point_id: Optional[str] = Field(None, description="Qdrant point ID for this chunk.")
    text: str = Field(..., description="Full text content of the chunk.")
    metadata: Dict[str, Any] = Field(default_factory=dict, description="Chunk metadata including source file, category, section, etc.")


class ChunksByPointIdsRequest(BaseModel):
    """Request body for querying chunks by Qdrant point IDs."""

    point_ids: List[str] = Field(..., max_length=50, description="List of Qdrant point IDs to look up.")

