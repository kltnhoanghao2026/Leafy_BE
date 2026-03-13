import shutil
import uuid
from pathlib import Path
from typing import Optional, Annotated, List

from fastapi import APIRouter, UploadFile, File, Form, BackgroundTasks, status, Depends

from app.core.security import get_current_user, UserPrincipal
from app.dto.ingestion.ingestion_dto import IngestResponse, TaskSchema
from app.dto.response.api_response import ApiResponse
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode
from app.services.vector_db import get_vector_service
from app.services.task_manager import get_task_manager
from app.utils.file_utils import validate_file_size, validate_mime_type, calculate_file_hash, sanitize_filename
from app.workers.document import process_document

router = APIRouter()

ALLOWED_MIME_TYPES = [
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain",
]

UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)


@router.post(
    "/ingest",
    response_model=ApiResponse[IngestResponse],
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
        Form(description="High-level domain category (e.g. `agronomy`, `regulation`)."),
    ] = None,
    variety: Annotated[
        Optional[str],
        Form(description="Crop variety or sub-category tag (e.g. `corn`, `wheat`)."),
    ] = None,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """
    Upload a document and queue it for asynchronous processing.

    **Processing pipeline:**
    1. Validates file size (≤ 20 MB) and MIME type.
    2. Persists the file to a temporary upload directory.
    3. Computes SHA-256 hash and checks for duplicates.
    4. Dispatches a background task: Parse → Chunk → Embed → Upsert into Qdrant.

    Poll `GET /api/v1/tasks/{task_id}` to track progress.
    The `user_id` is taken automatically from the authenticated user context.
    """
    # 1. Validation
    await validate_file_size(file, limit_mb=20)
    validate_mime_type(file, ALLOWED_MIME_TYPES)

    # 2. Save to temp
    safe_name = sanitize_filename(file.filename)
    task_id = str(uuid.uuid4())
    temp_filename = f"{task_id}_{safe_name}"
    temp_file_path = UPLOAD_DIR / temp_filename

    try:
        with open(temp_file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
    except Exception as e:
        raise AppException(ErrorCode.FILE_SAVE_FAILED, str(e))

    # 3. Hash & Deduplication
    file_hash = await calculate_file_hash(temp_file_path)

    vector_service = get_vector_service()
    if vector_service.check_existing_hash(file_hash):
        temp_file_path.unlink()
        result = IngestResponse(
            task_id=task_id,
            status="skipped",
            message="Document already exists.",
            file_id=file_hash,
        )
        return ApiResponse.success(result=result, message="Document already exists.")

    # 4. Prepare Metadata (user_id from auth context)
    metadata = {
        "original_filename": file.filename,
        "content_type": file.content_type,
        "user_id": current_user.id,
    }
    if category:
        metadata["category"] = category
    if variety:
        metadata["variety"] = variety

    # 5. Background Task
    task_manager = get_task_manager()
    task_manager.create_task(task_id, file_info=metadata)
    background_tasks.add_task(process_document, temp_file_path, metadata, file_hash, task_id)

    result = IngestResponse(
        task_id=task_id,
        status="accepted",
        message="Document accepted for processing.",
        file_id=file_hash,
    )
    return ApiResponse.success(result=result, message="Document accepted for processing.")


@router.get(
    "/tasks",
    response_model=ApiResponse[List[TaskSchema]],
    summary="List all ingestion tasks",
)
async def list_tasks(current_user: UserPrincipal = Depends(get_current_user)):
    """Returns the full list of document ingestion tasks tracked in-memory."""
    task_manager = get_task_manager()
    return ApiResponse.success(result=task_manager.list_tasks())


@router.get(
    "/tasks/{task_id}",
    response_model=ApiResponse[TaskSchema],
    summary="Get ingestion task status",
    responses={
        200: {"description": "Task found."},
        404: {"description": "No task found with the given ID."},
    },
)
async def get_task_status(
    task_id: str,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """Retrieve the current status of a single ingestion task by its UUID."""
    task_manager = get_task_manager()
    task = task_manager.get_task(task_id)
    if not task:
        raise AppException(ErrorCode.TASK_NOT_FOUND, f"Task '{task_id}' not found.")
    return ApiResponse.success(result=task)
