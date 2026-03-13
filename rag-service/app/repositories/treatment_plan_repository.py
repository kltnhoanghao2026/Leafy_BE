import logging
from typing import Dict, List, Optional, Any

import pymongo
from pymongo import MongoClient

from app.config.settings import settings

logger = logging.getLogger(__name__)

COLLECTION_NAME = "treatment_plans"


class TreatmentPlanRepository:
    """
    Repository for TreatmentPlan MongoDB documents.
    Follows the same pattern as DiagnoseRepository in disease-detection-service.
    """

    _instance = None

    def __new__(cls):
        if cls._instance is None:
            instance = super().__new__(cls)
            try:
                instance._client = MongoClient(settings.MONGODB_URI)
                instance._db = instance._client[settings.MONGODB_DATABASE_RAG]
                instance._collection = instance._db[COLLECTION_NAME]
                # Index for fast per-user queries
                instance._collection.create_index([("userId", pymongo.ASCENDING)])
                instance._collection.create_index([("planId", pymongo.ASCENDING)], unique=True)
                logger.info(
                    "TreatmentPlanRepository connected to MongoDB database '%s', collection '%s'",
                    settings.MONGODB_DATABASE_RAG,
                    COLLECTION_NAME,
                )
                cls._instance = instance
            except Exception as e:
                logger.error("TreatmentPlanRepository failed to initialize: %s", e)
                raise RuntimeError(f"MongoDB connection failed: {e}") from e
        return cls._instance

    # ─────────────────────────────────────────────────────────────────────────

    def save_plan(self, doc: Dict[str, Any]) -> str:
        """Insert a TreatmentPlan document. Returns the planId."""
        self._collection.insert_one(doc)
        logger.info("TreatmentPlan saved — planId=%s, userId=%s", doc.get("planId"), doc.get("userId"))
        return doc["planId"]

    def find_by_user(
        self,
        user_id: str,
        skip: int = 0,
        limit: int = 20,
    ) -> List[Dict[str, Any]]:
        """List treatment plans belonging to a user, newest first."""
        cursor = (
            self._collection.find({"userId": user_id}, {"_id": 0})
            .sort("createdAt", pymongo.DESCENDING)
            .skip(skip)
            .limit(limit)
        )
        return list(cursor)

    def find_all(self, skip: int = 0, limit: int = 50) -> List[Dict[str, Any]]:
        """List all treatment plans (ADMIN use), newest first."""
        cursor = (
            self._collection.find({}, {"_id": 0})
            .sort("createdAt", pymongo.DESCENDING)
            .skip(skip)
            .limit(limit)
        )
        return list(cursor)

    def find_by_id(self, plan_id: str) -> Optional[Dict[str, Any]]:
        """Retrieve a single plan by planId. Returns None if not found."""
        return self._collection.find_one({"planId": plan_id}, {"_id": 0})

    def delete_by_id(self, plan_id: str) -> bool:
        """Hard-delete a plan. Returns True if a document was deleted."""
        result = self._collection.delete_one({"planId": plan_id})
        deleted = result.deleted_count > 0
        if deleted:
            logger.info("TreatmentPlan deleted — planId=%s", plan_id)
        return deleted


def get_treatment_plan_repository() -> TreatmentPlanRepository:
    return TreatmentPlanRepository()
