import shutil
import uuid
from pathlib import Path
from typing import Optional, Annotated, List

from fastapi import APIRouter, UploadFile, File, Form, BackgroundTasks, HTTPException, status
from app.core.security import validate_file_size, validate_mime_type, calculate_file_hash, sanitize_filename
from app.services.vector_db import get_vector_service
from app.services.task_manager import get_task_manager
from app.workers.document import process_document
from app.schemas import IngestResponse, TaskSchema

router = APIRouter()

ALLOWED_MIME_TYPES = [
    "application/pdf", 
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 
    "text/plain"
]

UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)

@router.post(
    "/ingest",
    response_model=IngestResponse,
    status_code=status.HTTP_202_ACCEPTED,
    summary="Upload and ingest a document",
    responses={
        202: {"description": "Document accepted for background processing."},
        200: {"description": "Document skipped — identical content already indexed."},
        413: {"description": "File exceeds the 20 MB size limit."},
        415: {"description": "Unsupported MIME type."},
        500: {"description": "Server-side error while saving the upload."},
    },
)
async def ingest_document(
    background_tasks: BackgroundTasks,
    file: Annotated[
        UploadFile,
        File(description="Document to ingest. Accepted formats: PDF, DOCX, TXT. Max size: 20 MB."),
    ],
    category: Annotated[
        Optional[str],
        Form(description="High-level domain category for the document (e.g. `agronomy`, `regulation`)."),
    ] = None,
    variety: Annotated[
        Optional[str],
        Form(description="Crop variety or sub-category tag (e.g. `corn`, `wheat`)."),
    ] = None,
    user_id: Annotated[
        Optional[str],
        Form(description="ID of the user who owns this document. Stored in chunk metadata for row-level filtering."),
    ] = None,
):
    """
    Upload a document and queue it for asynchronous processing.

    **Processing pipeline:**
    1. Validates file size (≤ 20 MB) and MIME type.
    2. Persists the file to a temporary upload directory.
    3. Computes the **SHA-256** hash and checks for duplicates — already-indexed
       documents are skipped without error.
    4. Dispatches a background task that:
       - Parses the document (PDF / DOCX / TXT)
       - Splits the text into overlapping chunks
       - Embeds chunks with `fastembed`
       - Upserts into Qdrant with rich metadata

    Poll `GET /api/v1/tasks/{task_id}` to track progress.
    """
    # 1. Validation
    await validate_file_size(file, limit_mb=20)
    validate_mime_type(file, ALLOWED_MIME_TYPES)
    
    # 2. Save to Temp (we need file on disk for hash calc and background processing)
    # Use UUID to avoid collisions
    safe_name = sanitize_filename(file.filename)
    task_id = str(uuid.uuid4())
    temp_filename = f"{task_id}_{safe_name}"
    temp_file_path = UPLOAD_DIR / temp_filename
    
    try:
        with open(temp_file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, 
            detail="Failed to save upload file."
        )

    # 3. Hash & Deduplication
    file_hash = await calculate_file_hash(temp_file_path)
    
    vector_service = get_vector_service()
    if vector_service.check_existing_hash(file_hash):
        # Clean up dup file immediately
        temp_file_path.unlink()
        return IngestResponse(
            task_id=task_id,
            status="skipped",
            message="Document already exists.",
            file_id=file_hash
        )

    # 4. Prepare Metadata
    metadata = {
        "original_filename": file.filename,
        "content_type": file.content_type,
    }
    if category: metadata["category"] = category
    if variety: metadata["variety"] = variety
    if user_id: metadata["user_id"] = user_id

    # 5. Background Task
    task_manager = get_task_manager()
    task_manager.create_task(task_id, file_info=metadata)
    
    background_tasks.add_task(process_document, temp_file_path, metadata, file_hash, task_id)

    return IngestResponse(
        task_id=task_id,
        status="accepted",
        message="Document accepted for processing.",
        file_id=file_hash
    )

@router.get(
    "/tasks",
    response_model=List[TaskSchema],
    summary="List all ingestion tasks",
    responses={
        200: {"description": "Paginated list of all tasks (newest first)."},
    },
)
async def list_tasks():
    """
    Returns the full list of document ingestion tasks tracked in-memory for this
    server instance. Use this to see all ongoing or completed jobs.
    """
    task_manager = get_task_manager()
    return task_manager.list_tasks()

@router.get(
    "/tasks/{task_id}",
    response_model=TaskSchema,
    summary="Get ingestion task status",
    responses={
        200: {"description": "Task found — see `status` field for the current lifecycle state."},
        404: {"description": "No task found with the given ID."},
    },
)
async def get_task_status(task_id: str):
    """
    Retrieve the current status and metadata of a single ingestion task by its UUID.

    Possible `status` values:
    - **`pending`** — task is queued but not yet started
    - **`processing`** — document is actively being chunked / embedded
    - **`completed`** — vectors successfully upserted into Qdrant
    - **`failed`** — check the `error` field for details
    """
    task_manager = get_task_manager()
    task = task_manager.get_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    return task
