"""
DiagnoseRepository — MongoDB persistence layer for DiagnoseRequest and DiagnoseResult.
Uses synchronous pymongo (matching the rest of the service's synchronous design).
"""
import logging
from typing import Optional, List

from pymongo import MongoClient, DESCENDING
from pymongo.collection import Collection
from pymongo.errors import PyMongoError

from app.config.config import config

logger = logging.getLogger(__name__)

COLLECTION_REQUESTS = "diagnose_requests"
COLLECTION_RESULTS = "diagnose_results"


class DiagnoseRepository:
    """
    Handles MongoDB read/write operations for DiagnoseRequest and DiagnoseResult.
    The client is created lazily and shared across all requests.
    """

    _client: Optional[MongoClient] = None

    @classmethod
    def _get_client(cls) -> MongoClient:
        if cls._client is None:
            cls._client = MongoClient(
                config.MONGODB_URI,
                serverSelectionTimeoutMS=5000,
            )
            logger.info("MongoDB client initialised (disease-detection-service)")
        return cls._client

    @classmethod
    def _get_collection(cls, collection_name: str) -> Collection:
        db = cls._get_client()[config.MONGODB_DATABASE_DISEASE]
        return db[collection_name]

    # ------------------------------------------------------------------ #
    #  Write                                                               #
    # ------------------------------------------------------------------ #

    @classmethod
    def save_request(cls, doc: dict) -> str:
        try:
            cls._get_collection(COLLECTION_REQUESTS).insert_one(doc)
            logger.debug(f"DiagnoseRequest saved: {doc.get('diagnoseRequestId')}")
            return doc["diagnoseRequestId"]
        except PyMongoError as e:
            logger.error(f"Failed to save DiagnoseRequest: {e}")
            raise

    @classmethod
    def save_result(cls, doc: dict) -> str:
        try:
            cls._get_collection(COLLECTION_RESULTS).insert_one(doc)
            logger.debug(f"DiagnoseResult saved: {doc.get('diagnoseResultId')}")
            return doc["diagnoseResultId"]
        except PyMongoError as e:
            logger.error(f"Failed to save DiagnoseResult: {e}")
            raise

    @classmethod
    def update_plant_id(cls, diagnose_request_id: str, plant_id: str) -> bool:
        try:
            result = cls._get_collection(COLLECTION_REQUESTS).update_one(
                {"diagnoseRequestId": diagnose_request_id},
                {"$set": {"plantId": plant_id}}
            )
            return result.modified_count > 0 or result.matched_count > 0
        except PyMongoError as e:
            logger.error(f"Failed to update plantId for DiagnoseRequest {diagnose_request_id}: {e}")
            raise

    # ------------------------------------------------------------------ #
    #  Read — DiagnoseRequest                                              #
    # ------------------------------------------------------------------ #

    @classmethod
    def find_request_by_id(cls, diagnose_request_id: str) -> Optional[dict]:
        return cls._get_collection(COLLECTION_REQUESTS).find_one(
            {"diagnoseRequestId": diagnose_request_id},
            {"_id": 0},
        )

    @classmethod
    def find_all_requests(cls, skip: int = 0, limit: int = 20) -> List[dict]:
        cursor = (
            cls._get_collection(COLLECTION_REQUESTS)
            .find({}, {"_id": 0})
            .sort("timeStamp", DESCENDING)
            .skip(skip)
            .limit(limit)
        )
        return list(cursor)

    @classmethod
    def count_all_requests(cls) -> int:
        return cls._get_collection(COLLECTION_REQUESTS).count_documents({})

    @classmethod
    def find_requests_by_user(cls, user_id: str, skip: int = 0, limit: int = 20) -> List[dict]:
        cursor = (
            cls._get_collection(COLLECTION_REQUESTS)
            .find({"userId": user_id}, {"_id": 0})
            .sort("timeStamp", DESCENDING)
            .skip(skip)
            .limit(limit)
        )
        return list(cursor)

    @classmethod
    def count_requests_by_user(cls, user_id: str) -> int:
        return cls._get_collection(COLLECTION_REQUESTS).count_documents({"userId": user_id})

    # ------------------------------------------------------------------ #
    #  Read — DiagnoseResult                                              #
    # ------------------------------------------------------------------ #

    @classmethod
    def find_result_by_request_id(cls, diagnose_request_id: str) -> Optional[dict]:
        return cls._get_collection(COLLECTION_RESULTS).find_one(
            {"diagnoseRequestId": diagnose_request_id},
            {"_id": 0},
        )

    @classmethod
    def find_all_results(cls, skip: int = 0, limit: int = 20) -> List[dict]:
        cursor = (
            cls._get_collection(COLLECTION_RESULTS)
            .find({}, {"_id": 0})
            .sort("timeStamp", DESCENDING)
            .skip(skip)
            .limit(limit)
        )
        return list(cursor)

    @classmethod
    def count_all_results(cls) -> int:
        return cls._get_collection(COLLECTION_RESULTS).count_documents({})

    @classmethod
    def find_results_by_user(cls, user_id: str, skip: int = 0, limit: int = 20) -> List[dict]:
        cursor = (
            cls._get_collection(COLLECTION_RESULTS)
            .find({"userId": user_id}, {"_id": 0})
            .sort("timeStamp", DESCENDING)
            .skip(skip)
            .limit(limit)
        )
        return list(cursor)

    @classmethod
    def count_results_by_user(cls, user_id: str) -> int:
        return cls._get_collection(COLLECTION_RESULTS).count_documents({"userId": user_id})

    # ------------------------------------------------------------------ #
    #  Delete                                                              #
    # ------------------------------------------------------------------ #

    @classmethod
    def delete_request(cls, diagnose_request_id: str) -> bool:
        result = cls._get_collection(COLLECTION_REQUESTS).delete_one(
            {"diagnoseRequestId": diagnose_request_id}
        )
        return result.deleted_count > 0

    @classmethod
    def delete_result_by_request_id(cls, diagnose_request_id: str) -> bool:
        result = cls._get_collection(COLLECTION_RESULTS).delete_one(
            {"diagnoseRequestId": diagnose_request_id}
        )
        return result.deleted_count > 0

    # ------------------------------------------------------------------ #
    #  Lifecycle                                                           #
    # ------------------------------------------------------------------ #

    @classmethod
    def close(cls) -> None:
        if cls._client is not None:
            cls._client.close()
            cls._client = None
            logger.info("MongoDB client closed")
