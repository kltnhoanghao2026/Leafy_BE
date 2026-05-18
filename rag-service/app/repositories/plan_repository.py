import logging
from typing import Dict, List, Optional, Any

import pymongo
from pymongo import MongoClient

from app.config.settings import settings

logger = logging.getLogger(__name__)

COLLECTION_NAME = "plans"


class PlanRepository:
    """
    Repository for Plan MongoDB documents.
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
                    "PlanRepository connected to MongoDB database '%s', collection '%s'",
                    settings.MONGODB_DATABASE_RAG,
                    COLLECTION_NAME,
                )
                cls._instance = instance
            except Exception as e:
                logger.error("PlanRepository failed to initialize: %s", e)
                raise RuntimeError(f"MongoDB connection failed: {e}") from e
        return cls._instance

    # ─────────────────────────────────────────────────────────────────────────

    def save_plan(self, doc: Dict[str, Any]) -> str:
        """Insert a Plan document. Returns the planId."""
        self._collection.insert_one(doc)
        logger.info("Plan saved — planId=%s, userId=%s", doc.get("planId"), doc.get("userId"))
        return doc["planId"]

    def save_rag_plan(
        self,
        *,
        generated_plan: Dict[str, Any],
        source_documents: Optional[List[Dict[str, Any]]] = None,
        web_search_results: Optional[List[Dict[str, Any]]] = None,
        user_id: str,
        plant_management_plan_id: Optional[str] = None,
    ) -> str:
        """Save a RAG-generated plan to rag-service MongoDB.

        Returns the local rag-service planId. If plant_management_plan_id is provided,
        it is stored as a cross-reference (useful for linking the two plan records).
        """
        import uuid
        from datetime import datetime, timezone

        plan_id = str(uuid.uuid4())
        now = datetime.now(timezone.utc)

        # Strip empty {} entries from sourceDocuments — they indicate
        # no meaningful retrieval context was available at generation time.
        docs = [
            d for d in (source_documents or [])
            if d and isinstance(d, dict) and any(v for v in d.values() if v)
        ]
        web = [
            w for w in (web_search_results or [])
            if w and isinstance(w, dict) and any(v for v in w.values() if v)
        ]

        doc: Dict[str, Any] = {
            "planId": plan_id,
            "planName": generated_plan.get("planName"),
            "diseaseName": generated_plan.get("diseaseName"),
            "confidenceScore": generated_plan.get("confidenceScore"),
            "severityLevel": generated_plan.get("severityLevel"),
            "requiredInputs": generated_plan.get("requiredInputs") or [],
            "safetyWarnings": generated_plan.get("safetyWarnings") or [],
            "successIndicators": generated_plan.get("successIndicators"),
            "estimatedCost": generated_plan.get("estimatedCost"),
            "sourceType": "RAG_GEN",
            "source": generated_plan.get("source"),
            "sourceDocuments": docs,
            "webSearchResults": web,
            "plantId": generated_plan.get("plantId"),
            "farmPlotId": generated_plan.get("farmPlotId"),
            "farmZoneId": generated_plan.get("farmZoneId"),
            "schedule": generated_plan.get("schedule") or [],
            "isPublic": False,
            "active": True,
            "creatorId": user_id,
            "ownerId": user_id,
            "userId": user_id,
            "plantManagementPlanId": plant_management_plan_id,
            "createdAt": now,
            "lastModifiedAt": now,
        }
        self._collection.insert_one(doc)
        logger.info(
            "RAG plan saved — planId=%s, pm_planId=%s, userId=%s, docs=%d, web=%d",
            plan_id, plant_management_plan_id, user_id, len(docs), len(web),
        )
        return plan_id

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
            logger.info("Plan deleted — planId=%s", plan_id)
        return deleted


def get_plan_repository() -> PlanRepository:
    return PlanRepository()
