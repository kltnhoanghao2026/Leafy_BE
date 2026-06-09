import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Literal, Optional

from pydantic import BaseModel, Field


class ConversationPipelineState(BaseModel):
    ragState: Optional[str] = None
    currentNode: Optional[str] = None
    step: Optional[int] = None


class ConversationResponseMeta(BaseModel):
    documentsCount: int = 0
    webResultsCount: int = 0
    savedPlanId: Optional[str] = None
    plan: Optional[Dict[str, Any]] = None
    documents: Optional[List[Dict[str, Any]]] = None
    webResults: Optional[List[Dict[str, Any]]] = None


class ConversationMessageDoc(BaseModel):
    messageId: str = Field(default_factory=lambda: str(uuid.uuid4()))
    role: Literal["user", "assistant"]
    text: str
    createdAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    pipeline: Optional[ConversationPipelineState] = None
    responseMeta: Optional[ConversationResponseMeta] = None


class ConversationDoc(BaseModel):
    conversationId: str = Field(default_factory=lambda: str(uuid.uuid4()))
    userId: str
    threadId: str
    title: str
    preview: str
    messageCount: int = 0
    messages: List[ConversationMessageDoc] = Field(default_factory=list)
    lastPipelineState: Optional[ConversationPipelineState] = None
    createdAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    updatedAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    isDeleted: bool = False
    deletedAt: Optional[datetime] = None
