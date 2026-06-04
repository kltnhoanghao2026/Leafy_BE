import logging
from typing import List

from fastapi import APIRouter, Depends, Query, Request
from pydantic import BaseModel, Field

from app.core.security import UserPrincipal, get_current_user
from app.dto.response.api_response import ApiResponse
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode
from app.i18n import get_message, resolve_locale
from app.repositories.conversation_repository import get_conversation_repository

logger = logging.getLogger(__name__)

router = APIRouter(redirect_slashes=False)


class RenameConversationRequest(BaseModel):
    title: str = Field(..., min_length=1, max_length=120)


@router.get(
    "",
    response_model=ApiResponse[List[dict]],
    summary="List conversations",
    description=(
        "Returns a paginated list of active (non-deleted) conversations for the "
        "authenticated user, ordered by `updatedAt` descending. "
        "Each item contains lightweight fields only (`conversationId`, `threadId`, "
        "`title`, `preview`, `messageCount`, `updatedAt`). "
        "Use `GET /{conversation_id}` to load the full message history."
    ),
)
async def list_conversations(
    request: Request,
    page: int = Query(0, ge=0, description="Zero-based page number."),
    size: int = Query(20, ge=1, le=100, description="Number of conversations per page."),
    current_user: UserPrincipal = Depends(get_current_user),
):
    repo = get_conversation_repository()
    conversations = repo.list_by_user(
        user_id=current_user.id,
        skip=page * size,
        limit=size,
    )

    logger.info(
        "Conversation list - userId=%s page=%d size=%d returned=%d",
        current_user.id,
        page,
        size,
        len(conversations),
    )
    locale = resolve_locale(request)
    return ApiResponse.success(data=conversations, locale=locale)


@router.get(
    "/{conversation_id}",
    response_model=ApiResponse[dict],
    summary="Get a conversation",
    description=(
        "Returns the full conversation document including all messages and "
        "`lastPipelineState`. Admins can access any conversation; regular users "
        "can only access their own."
    ),
)
async def get_conversation(
    request: Request,
    conversation_id: str,
    current_user: UserPrincipal = Depends(get_current_user),
):
    repo = get_conversation_repository()
    is_admin = "ROLE_ADMIN" in current_user.roles

    conversation = repo.get_by_id_for_user(
        conversation_id=conversation_id,
        user_id=current_user.id,
        is_admin=is_admin,
    )
    if not conversation:
        raise AppException(ErrorCode.CONVERSATION_NOT_FOUND)

    logger.info(
        "Conversation loaded - conversationId=%s userId=%s",
        conversation_id,
        current_user.id,
    )
    locale = resolve_locale(request)
    return ApiResponse.success(data=conversation, locale=locale)


@router.patch(
    "/{conversation_id}",
    response_model=ApiResponse[dict],
    summary="Rename a conversation",
    description=(
        "Updates the `title` field for a conversation owned by the authenticated user. "
        "The new title is trimmed and capped at 120 characters. "
        "Returns the updated conversation document."
    ),
)
async def rename_conversation(
    request: Request,
    conversation_id: str,
    body: RenameConversationRequest,
    current_user: UserPrincipal = Depends(get_current_user),
):
    repo = get_conversation_repository()
    is_admin = "ROLE_ADMIN" in current_user.roles

    existing = repo.find_active_by_id(conversation_id)
    if not existing:
        raise AppException(ErrorCode.CONVERSATION_NOT_FOUND)

    if not is_admin and existing.get("userId") != current_user.id:
        raise AppException(ErrorCode.CONVERSATION_ACCESS_DENIED)

    renamed = repo.rename_for_user(
        conversation_id=conversation_id,
        user_id=current_user.id,
        title=body.title,
        is_admin=is_admin,
    )
    if not renamed:
        raise AppException(ErrorCode.CONVERSATION_NOT_FOUND)

    refreshed = repo.get_by_id_for_user(
        conversation_id=conversation_id,
        user_id=current_user.id,
        is_admin=is_admin,
    )

    logger.info(
        "Conversation renamed - conversationId=%s userId=%s title=%s",
        conversation_id,
        current_user.id,
        body.title,
    )
    locale = resolve_locale(request)
    return ApiResponse.success(data=refreshed or {}, locale=locale)


@router.delete(
    "/{conversation_id}",
    response_model=ApiResponse[None],
    summary="Soft delete a conversation",
    description=(
        "Soft-deletes a conversation by setting `isDeleted=true` and recording `deletedAt`. "
        "The record is retained in MongoDB but excluded from all list/get endpoints. "
        "Only the owning user or an admin can delete a conversation."
    ),
)
async def delete_conversation(
    request: Request,
    conversation_id: str,
    current_user: UserPrincipal = Depends(get_current_user),
):
    repo = get_conversation_repository()
    is_admin = "ROLE_ADMIN" in current_user.roles

    existing = repo.find_active_by_id(conversation_id)
    if not existing:
        raise AppException(ErrorCode.CONVERSATION_NOT_FOUND)

    if not is_admin and existing.get("userId") != current_user.id:
        raise AppException(ErrorCode.CONVERSATION_ACCESS_DENIED)

    deleted = repo.soft_delete_for_user(
        conversation_id=conversation_id,
        user_id=current_user.id,
        is_admin=is_admin,
    )
    if not deleted:
        raise AppException(ErrorCode.CONVERSATION_NOT_FOUND)

    logger.info(
        "Conversation soft-deleted - conversationId=%s by userId=%s",
        conversation_id,
        current_user.id,
    )

    locale = resolve_locale(request)
    return ApiResponse.success(
        message=get_message("response.conversation.deleted", locale),
        locale=locale,
    )
