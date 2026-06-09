import logging
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

import pymongo
from pymongo import MongoClient

from app.config.settings import settings
from app.models.conversation_doc import (
    ConversationDoc,
    ConversationMessageDoc,
    ConversationPipelineState,
    ConversationResponseMeta,
)

logger = logging.getLogger(__name__)

COLLECTION_NAME = "conversations"


class ConversationRepository:
    _instance = None

    def __new__(cls):
        if cls._instance is None:
            instance = super().__new__(cls)
            try:
                instance._client = MongoClient(settings.MONGODB_URI)
                instance._db = instance._client[settings.MONGODB_DATABASE_RAG]
                instance._collection = instance._db[COLLECTION_NAME]

                instance._collection.create_index(
                    [("userId", pymongo.ASCENDING), ("updatedAt", pymongo.DESCENDING)]
                )
                instance._collection.create_index(
                    [("conversationId", pymongo.ASCENDING)],
                    unique=True,
                )
                instance._collection.create_index(
                    [("userId", pymongo.ASCENDING), ("threadId", pymongo.ASCENDING), ("isDeleted", pymongo.ASCENDING)]
                )
                instance._collection.create_index([("isDeleted", pymongo.ASCENDING)])

                logger.info(
                    "ConversationRepository connected to MongoDB database '%s', collection '%s'",
                    settings.MONGODB_DATABASE_RAG,
                    COLLECTION_NAME,
                )
                cls._instance = instance
            except Exception as e:
                logger.error("ConversationRepository failed to initialize: %s", e)
                raise RuntimeError(f"MongoDB connection failed: {e}") from e
        return cls._instance

    def _now(self) -> datetime:
        return datetime.now(timezone.utc)

    def _shorten(self, text: str, max_len: int) -> str:
        compact = " ".join(text.strip().split())
        if len(compact) <= max_len:
            return compact
        return f"{compact[: max_len - 1].rstrip()}..."

    def _derive_title(self, question: str) -> str:
        title = self._shorten(question, 80)
        return title or "New conversation"

    def _derive_preview(self, answer: str) -> str:
        preview = self._shorten(answer, 160)
        return preview or "No assistant response yet"

    def _base_projection(self) -> Dict[str, int]:
        return {"_id": 0}

    def find_active_by_thread(self, user_id: str, thread_id: str) -> Optional[Dict[str, Any]]:
        return self._collection.find_one(
            {
                "userId": user_id,
                "threadId": thread_id,
                "isDeleted": {"$ne": True},
            },
            self._base_projection(),
        )

    def find_active_by_id(self, conversation_id: str) -> Optional[Dict[str, Any]]:
        return self._collection.find_one(
            {
                "conversationId": conversation_id,
                "isDeleted": {"$ne": True},
            },
            self._base_projection(),
        )

    def list_by_user(self, user_id: str, skip: int = 0, limit: int = 20) -> List[Dict[str, Any]]:
        projection = {
            "_id": 0,
            "conversationId": 1,
            "threadId": 1,
            "title": 1,
            "preview": 1,
            "messageCount": 1,
            "createdAt": 1,
            "updatedAt": 1,
            "lastPipelineState": 1,
        }
        cursor = (
            self._collection.find(
                {
                    "userId": user_id,
                    "isDeleted": {"$ne": True},
                },
                projection,
            )
            .sort("updatedAt", pymongo.DESCENDING)
            .skip(skip)
            .limit(limit)
        )
        return list(cursor)

    def get_by_id_for_user(
        self,
        conversation_id: str,
        user_id: str,
        *,
        is_admin: bool = False,
    ) -> Optional[Dict[str, Any]]:
        query: Dict[str, Any] = {
            "conversationId": conversation_id,
            "isDeleted": {"$ne": True},
        }
        if not is_admin:
            query["userId"] = user_id

        return self._collection.find_one(query, self._base_projection())

    def upsert_turn(
        self,
        *,
        user_id: str,
        thread_id: str,
        question: str,
        answer: str,
        pipeline_state: Optional[Dict[str, Any]] = None,
        response_meta: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        now = self._now()
        pipeline_doc = ConversationPipelineState(**(pipeline_state or {})).model_dump(exclude_none=True) or None
        response_meta_doc = ConversationResponseMeta(**(response_meta or {})).model_dump(exclude_none=True) or None

        user_message = ConversationMessageDoc(
            role="user",
            text=question,
            createdAt=now,
        ).model_dump(mode="json", exclude_none=True)

        assistant_message = ConversationMessageDoc(
            role="assistant",
            text=answer,
            createdAt=now,
            pipeline=ConversationPipelineState(**pipeline_state) if pipeline_state else None,
            responseMeta=ConversationResponseMeta(**response_meta) if response_meta else None,
        ).model_dump(mode="json", exclude_none=True)

        existing = self.find_active_by_thread(user_id, thread_id)
        preview = self._derive_preview(answer)

        if existing:
            self._collection.update_one(
                {
                    "conversationId": existing["conversationId"],
                },
                {
                    "$push": {"messages": {"$each": [user_message, assistant_message]}},
                    "$set": {
                        "preview": preview,
                        "updatedAt": now,
                        "lastPipelineState": pipeline_doc,
                    },
                    "$inc": {"messageCount": 2},
                },
            )
            return {
                "conversationId": existing["conversationId"],
                "threadId": thread_id,
                "title": existing.get("title") or self._derive_title(question),
                "preview": preview,
            }

        doc = ConversationDoc(
            userId=user_id,
            threadId=thread_id,
            title=self._derive_title(question),
            preview=preview,
            messageCount=2,
            messages=[
                ConversationMessageDoc(**user_message),
                ConversationMessageDoc(**assistant_message),
            ],
            lastPipelineState=ConversationPipelineState(**pipeline_state) if pipeline_state else None,
            createdAt=now,
            updatedAt=now,
        ).model_dump(mode="json", exclude_none=True)

        self._collection.insert_one(doc)
        return {
            "conversationId": doc["conversationId"],
            "threadId": thread_id,
            "title": doc["title"],
            "preview": doc["preview"],
        }

    def rename_for_user(
        self,
        *,
        conversation_id: str,
        user_id: str,
        title: str,
        is_admin: bool = False,
    ) -> bool:
        query: Dict[str, Any] = {
            "conversationId": conversation_id,
            "isDeleted": {"$ne": True},
        }
        if not is_admin:
            query["userId"] = user_id

        trimmed = self._shorten(title, 120)
        if not trimmed:
            return False

        result = self._collection.update_one(
            query,
            {
                "$set": {
                    "title": trimmed,
                    "updatedAt": self._now(),
                }
            },
        )
        return result.modified_count > 0

    def soft_delete_for_user(
        self,
        *,
        conversation_id: str,
        user_id: str,
        is_admin: bool = False,
    ) -> bool:
        query: Dict[str, Any] = {
            "conversationId": conversation_id,
            "isDeleted": {"$ne": True},
        }
        if not is_admin:
            query["userId"] = user_id

        now = self._now()
        result = self._collection.update_one(
            query,
            {
                "$set": {
                    "isDeleted": True,
                    "deletedAt": now,
                    "updatedAt": now,
                }
            },
        )
        return result.modified_count > 0


def get_conversation_repository() -> ConversationRepository:
    return ConversationRepository()
