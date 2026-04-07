"""
Task Manager — MongoDB-backed

Replaces the previous in-memory dict store with a MongoDB-backed implementation
so ingestion task history survives service restarts.

On initialization, pre-loads all existing task records from MongoDB into an
in-memory cache for O(1) reads. Every create/update is written through to the DB.
"""

import logging
from typing import Dict, List, Optional
from datetime import datetime
from enum import Enum

import pymongo
from pymongo import MongoClient
from pydantic import BaseModel

from app.config.settings import settings

logger = logging.getLogger(__name__)

_COLLECTION_NAME = "ingestion_tasks"


class TaskStatus(str, Enum):
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"


class TaskInfo(BaseModel):
    task_id: str
    status: TaskStatus
    created_at: datetime
    updated_at: datetime
    message: Optional[str] = None
    file_info: Optional[Dict] = None
    error: Optional[str] = None


class TaskManager:
    _instance = None

    def __new__(cls):
        if cls._instance is None:
            instance = super(TaskManager, cls).__new__(cls)
            try:
                instance._initialize()
                cls._instance = instance
            except Exception as e:
                logger.error("TaskManager failed to initialize MongoDB backend: %s", e)
                # Fall back to in-memory only so the service still starts
                instance._tasks: Dict[str, TaskInfo] = {}
                instance._collection = None
                cls._instance = instance
        return cls._instance

    # ─────────────────────────────────────────────────────────────────────────

    def _initialize(self):
        client = MongoClient(settings.MONGODB_URI)
        db = client[settings.MONGODB_DATABASE_RAG]
        self._collection = db[_COLLECTION_NAME]
        self._collection.create_index(
            [("task_id", pymongo.ASCENDING)], unique=True
        )

        # Pre-load existing tasks into the in-memory cache
        self._tasks: Dict[str, TaskInfo] = {}
        for doc in self._collection.find({}, {"_id": 0}):
            try:
                self._tasks[doc["task_id"]] = TaskInfo(**doc)
            except Exception as parse_err:
                logger.warning("Skipping malformed task document: %s", parse_err)

        logger.info(
            "TaskManager initialized — %d existing tasks loaded from MongoDB",
            len(self._tasks),
        )

    # ─────────────────────────────────────────────────────────────────────────

    def create_task(self, task_id: str, file_info: Optional[Dict] = None) -> TaskInfo:
        now = datetime.utcnow()
        task = TaskInfo(
            task_id=task_id,
            status=TaskStatus.PENDING,
            created_at=now,
            updated_at=now,
            message="Task created",
            file_info=file_info,
        )
        self._tasks[task_id] = task

        if self._collection is not None:
            try:
                self._collection.insert_one(task.model_dump(mode="json"))
            except Exception as e:
                logger.error("Failed to persist task %s to MongoDB: %s", task_id, e)

        return task

    def update_task(
        self,
        task_id: str,
        status: TaskStatus,
        message: str = None,
        error: str = None,
    ):
        if task_id not in self._tasks:
            return

        task = self._tasks[task_id]
        task.status = status
        task.updated_at = datetime.utcnow()
        if message:
            task.message = message
        if error:
            task.error = error

        if self._collection is not None:
            try:
                self._collection.replace_one(
                    {"task_id": task_id},
                    task.model_dump(mode="json"),
                )
            except Exception as e:
                logger.error("Failed to update task %s in MongoDB: %s", task_id, e)

    def get_task(self, task_id: str) -> Optional[TaskInfo]:
        return self._tasks.get(task_id)

    def list_tasks(self) -> List[TaskInfo]:
        return list(self._tasks.values())


def get_task_manager():
    return TaskManager()

