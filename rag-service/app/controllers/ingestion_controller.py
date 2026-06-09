import os
import shutil
import uuid
from pathlib import Path
from typing import Optional, Annotated, List

from fastapi import APIRouter, UploadFile, File, Form, BackgroundTasks, status, Depends, Request, Query

from app.core.security import get_current_user, UserPrincipal
from app.dto.ingestion.ingestion_dto import (
    ChunkPreview,
    DocumentDetail,
    DocumentSummary,
    IngestResponse,
    PreviewResponse,
    TaskSchema,
)
from app.dto.response.api_response import ApiResponse
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode
from app.repositories.chunk_repository import get_chunk_repository
from app.repositories.document_repository import get_document_repository
from app.services.vector_db import get_vector_service
from app.services.task_manager import get_task_manager
from app.utils.file_utils import validate_file_size, validate_mime_type, calculate_file_hash, sanitize_filename
from app.workers.document import process_document, preview_document
from app.i18n import get_message, resolve_locale

router = APIRouter()

ALLOWED_MIME_TYPES = [
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain",
    "text/markdown",
]

UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)


# ═══════════════════════════════════════════════════════════════════════════════
# INGEST
# ═══════════════════════════════════════════════════════════════════════════════


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
    request: Request,
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
    except Exception:
        raise AppException(ErrorCode.FILE_SAVE_FAILED)

    locale = resolve_locale(request)

    # 3. Hash & Deduplication
    file_hash = await calculate_file_hash(temp_file_path)

    vector_service = get_vector_service()
    if vector_service.check_existing_hash(file_hash):
        temp_file_path.unlink()
        result = IngestResponse(
            task_id=task_id,
            status="skipped",
            message=get_message("response.document.exists", locale),
            file_id=file_hash,
        )
        return ApiResponse.success(data=result, message=get_message("response.document.exists", locale), locale=locale)

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
        message=get_message("response.document.accepted", locale),
        file_id=file_hash,
    )
    return ApiResponse.success(data=result, message=get_message("response.document.accepted", locale), locale=locale)


# ═══════════════════════════════════════════════════════════════════════════════
# PREVIEW (dry-run parsing — no persistence)
# ═══════════════════════════════════════════════════════════════════════════════


@router.post(
    "/preview",
    response_model=ApiResponse[PreviewResponse],
    summary="Preview document chunks without ingesting",
    responses={
        200: {"description": "Chunk preview returned successfully."},
        413: {"description": "File exceeds the 20 MB size limit."},
        415: {"description": "Unsupported MIME type."},
    },
)
async def preview_document_endpoint(
    file: Annotated[
        UploadFile,
        File(description="Document to preview. Accepted formats: PDF, DOCX, TXT. Max size: 20 MB."),
    ],
    current_user: UserPrincipal = Depends(get_current_user),
):
    """
    Parse and chunk a document **without** persisting anything.

    Use this to preview what chunks will be created before committing
    to a full ingestion via ``POST /ingest``.
    """
    # Validation
    await validate_file_size(file, limit_mb=20)
    validate_mime_type(file, ALLOWED_MIME_TYPES)

    # Save to temp
    safe_name = sanitize_filename(file.filename)
    temp_id = str(uuid.uuid4())
    temp_file_path = UPLOAD_DIR / f"{temp_id}_{safe_name}"

    try:
        with open(temp_file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
    except Exception:
        raise AppException(ErrorCode.FILE_SAVE_FAILED)

    try:
        chunks_raw, sections = preview_document(temp_file_path)

        chunk_previews = [
            ChunkPreview(
                index=i,
                text=c["text"],
            )
            for i, c in enumerate(chunks_raw)
        ]

        result = PreviewResponse(
            filename=file.filename or safe_name,
            total_chunks=len(chunk_previews),
            sections=sections,
            chunks=chunk_previews,
        )
        return ApiResponse.success(data=result)

    finally:
        # Always clean up
        if temp_file_path.exists():
            os.remove(temp_file_path)


# ═══════════════════════════════════════════════════════════════════════════════
# TASKS
# ═══════════════════════════════════════════════════════════════════════════════


def _to_task_schema(task, locale: str) -> TaskSchema:
    return TaskSchema(
        task_id=task.task_id,
        status=task.status,
        created_at=task.created_at,
        updated_at=task.updated_at,
        message=get_message(task.message, locale) if task.message else None,
        file_info=task.file_info,
        error=task.error,
    )


@router.get(
    "/tasks",
    response_model=ApiResponse[List[TaskSchema]],
    summary="List all ingestion tasks",
)
async def list_tasks(
    request: Request,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """Returns the full list of document ingestion tasks tracked in-memory."""
    locale = resolve_locale(request)
    task_manager = get_task_manager()
    tasks = [_to_task_schema(task, locale) for task in task_manager.list_tasks()]
    return ApiResponse.success(data=tasks, locale=locale)


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
    request: Request,
    task_id: str,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """Retrieve the current status of a single ingestion task by its UUID."""
    task_manager = get_task_manager()
    task = task_manager.get_task(task_id)
    if not task:
        raise AppException(ErrorCode.TASK_NOT_FOUND)
    locale = resolve_locale(request)
    return ApiResponse.success(data=_to_task_schema(task, locale), locale=locale)


# ═══════════════════════════════════════════════════════════════════════════════
# DOCUMENT CATALOG
# ═══════════════════════════════════════════════════════════════════════════════


@router.get(
    "/documents",
    response_model=ApiResponse[List[DocumentSummary]],
    summary="List all ingested documents",
)
async def list_documents(
    skip: int = Query(0, ge=0, description="Number of records to skip."),
    limit: int = Query(50, ge=1, le=200, description="Maximum number of records to return."),
    current_user: UserPrincipal = Depends(get_current_user),
):
    """Return all documents in the ingested-documents catalog, newest first."""
    doc_repo = get_document_repository()
    docs = doc_repo.find_all(skip=skip, limit=limit)
    summaries = [DocumentSummary(**d) for d in docs]
    return ApiResponse.success(data=summaries)


@router.get(
    "/documents/{document_id}",
    response_model=ApiResponse[DocumentDetail],
    summary="Get document detail with chunks",
    responses={
        200: {"description": "Document detail returned."},
        404: {"description": "No document found with the given ID."},
    },
)
async def get_document_detail(
    document_id: str,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """Retrieve a single document's metadata and all its chunks."""
    doc_repo = get_document_repository()
    doc = doc_repo.find_by_id(document_id)
    if not doc:
        raise AppException(ErrorCode.TASK_NOT_FOUND)

    # Fetch chunks from chunk_repository
    chunk_repo = get_chunk_repository()
    raw_chunks = chunk_repo.find_by_document_id(document_id)

    chunk_previews = [
        ChunkPreview(
            index=c.get("chunk_index", i),
            text=c.get("text", ""),
            point_id=c.get("point_id"),
        )
        for i, c in enumerate(raw_chunks)
    ]

    detail = DocumentDetail(
        **doc,
        chunks=chunk_previews,
    )
    return ApiResponse.success(data=detail)


@router.delete(
    "/documents/{document_id}",
    response_model=ApiResponse,
    summary="Delete an ingested document",
    responses={
        200: {"description": "Document and its chunks removed."},
        404: {"description": "No document found with the given ID."},
    },
)
async def delete_document(
    document_id: str,
    current_user: UserPrincipal = Depends(get_current_user),
):
    """Remove a document from the catalog, MongoDB chunks, and Qdrant vectors."""
    doc_repo = get_document_repository()
    doc = doc_repo.find_by_id(document_id)
    if not doc:
        raise AppException(ErrorCode.TASK_NOT_FOUND)

    # 1. Remove chunks from MongoDB
    chunk_repo = get_chunk_repository()
    chunk_repo.delete_by_document_id(document_id)

    # 2. Remove vectors from Qdrant
    try:
        from qdrant_client import models as qdrant_models

        vector_service = get_vector_service()
        vector_service.client.delete(
            collection_name=vector_service.collection_name,
            points_selector=qdrant_models.FilterSelector(
                filter=qdrant_models.Filter(
                    must=[
                        qdrant_models.FieldCondition(
                            key="metadata.file_hash",
                            match=qdrant_models.MatchValue(value=document_id),
                        )
                    ]
                )
            ),
        )
        # Invalidate BM25 cache since vectors were removed
        vector_service.invalidate_bm25()
    except Exception as exc:
        import logging
        logging.getLogger(__name__).warning("Failed to delete vectors from Qdrant: %s", exc)

    # 3. Remove document record
    doc_repo.delete_by_id(document_id)

    return ApiResponse.success(message="Document deleted successfully.")
