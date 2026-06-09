"""
Chunks Controller — Query document chunks by Qdrant point IDs.

Provides a dedicated endpoint for fetching the full text and metadata of
retrieved document chunks, enabling the frontend source-document viewer modal
to display detailed chunk information without needing direct Qdrant access.
"""

import logging
from typing import List

from fastapi import APIRouter, Depends, Query

from app.core.security import get_current_user, UserPrincipal
from app.dto.response.api_response import ApiResponse
from app.dto.ingestion.ingestion_dto import ChunkDetail
from app.repositories.chunk_repository import get_chunk_repository

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get(
    "/chunks/by-point-ids",
    response_model=ApiResponse[List[ChunkDetail]],
    summary="Get chunk details by Qdrant point IDs",
    description=(
        "Fetch the full text and metadata of one or more document chunks "
        "by their Qdrant point IDs.  Used by the plan-detail source-document "
        "viewer to display rich chunk information (section, score, source file, "
        "and the full chunk text) when a user clicks on a source document card."
    ),
)
async def get_chunks_by_point_ids(
    point_ids: List[str] = Query(
        ...,
        max_length=50,
        description="List of Qdrant point IDs to look up.",
    ),
    current_user: UserPrincipal = Depends(get_current_user),
) -> ApiResponse[List[ChunkDetail]]:
    """
    Retrieve full chunk records from MongoDB by their Qdrant point IDs.

    The ``point_ids`` list is limited to 50 entries per request to prevent
    oversized payloads.  Results are sorted by ``chunk_index`` so they appear
    in their natural document order.
    """
    logger.info(
        "[CHUNKS] User %s requesting %d chunks by point IDs",
        current_user.id,
        len(point_ids),
    )

    chunk_repo = get_chunk_repository()
    raw_chunks = chunk_repo.find_by_point_ids(point_ids)

    chunk_details = [
        ChunkDetail(
            chunk_id=c.get("chunk_id", ""),
            document_id=c.get("document_id", ""),
            chunk_index=c.get("chunk_index", i),
            point_id=c.get("point_id"),
            text=c.get("text", ""),
            metadata=c.get("metadata", {}),
        )
        for i, c in enumerate(raw_chunks)
    ]

    logger.info(
        "[CHUNKS] Returning %d chunks for %d requested point IDs",
        len(chunk_details),
        len(point_ids),
    )
    return ApiResponse.success(data=chunk_details)
