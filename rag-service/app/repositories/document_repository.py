"""
Document Repository — MongoDB-backed catalog of all ingested documents.

Tracks every document that has been successfully ingested into the RAG
pipeline, including file-service references, chunk statistics, and
section breakdown.
"""

import logging
from datetime import datetime
from typing import Any, Dict, List, Optional

import pymongo
from pymongo import MongoClient

from app.config.settings import settings

logger = logging.getLogger(__name__)

_COLLECTION_NAME = "ingested_documents"


class DocumentRepository:
    """Singleton that manages the ``ingested_documents`` MongoDB collection."""

    _instance: Optional["DocumentRepository"] = None

    def __new__(cls) -> "DocumentRepository":
        if cls._instance is None:
            instance = super().__new__(cls)
            try:
                instance._initialize()
                cls._instance = instance
            except Exception as exc:
                logger.error("DocumentRepository failed to initialize: %s", exc)
                cls._instance = None
                raise
        return cls._instance

    # ─────────────────────────────────────────────────────────────────────────

    def _initialize(self) -> None:
        client: MongoClient = MongoClient(settings.MONGODB_URI)
        db = client[settings.MONGODB_DATABASE_RAG]
        self._collection = db[_COLLECTION_NAME]

        # Indexes
        self._collection.create_index(
            [("document_id", pymongo.ASCENDING)], unique=True
        )
        self._collection.create_index(
            [("user_id", pymongo.ASCENDING)],
        )
        self._collection.create_index(
            [("category", pymongo.ASCENDING)],
        )
        self._collection.create_index(
            [("ingested_at", pymongo.DESCENDING)],
        )

        logger.info(
            "DocumentRepository initialized — collection: %s", _COLLECTION_NAME
        )

    # ─────────────────────────────────────────────────────────────────────────

    def save_document(self, doc: Dict[str, Any]) -> None:
        """Insert or replace a document record."""
        doc.setdefault("ingested_at", datetime.utcnow())
        try:
            self._collection.replace_one(
                {"document_id": doc["document_id"]},
                doc,
                upsert=True,
            )
            logger.info(
                "Saved document record: document_id=%s, filename=%s",
                doc["document_id"],
                doc.get("original_filename"),
            )
        except Exception as exc:
            logger.error("Failed to save document record: %s", exc)
            raise

    def find_all(
        self,
        skip: int = 0,
        limit: int = 50,
    ) -> List[Dict[str, Any]]:
        """Return all documents, most recent first."""
        return list(
            self._collection.find({}, {"_id": 0})
            .sort("ingested_at", pymongo.DESCENDING)
            .skip(skip)
            .limit(limit)
        )

    def count_all(self) -> int:
        """Return total number of ingested documents."""
        return self._collection.count_documents({})

    def find_by_id(self, document_id: str) -> Optional[Dict[str, Any]]:
        """Find a single document by its hash ID."""
        return self._collection.find_one(
            {"document_id": document_id}, {"_id": 0}
        )

    def delete_by_id(self, document_id: str) -> bool:
        """Delete a document record. Returns True if a record was deleted."""
        result = self._collection.delete_one({"document_id": document_id})
        deleted = result.deleted_count > 0
        if deleted:
            logger.info("Deleted document record: document_id=%s", document_id)
        return deleted


def get_document_repository() -> DocumentRepository:
    """Global singleton accessor."""
    return DocumentRepository()
