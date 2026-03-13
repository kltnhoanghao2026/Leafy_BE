from typing import Dict, List, Optional
from datetime import datetime
from enum import Enum
from pydantic import BaseModel

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
            cls._instance = super(TaskManager, cls).__new__(cls)
            cls._instance._initialize()
        return cls._instance
    
    def _initialize(self):
        self._tasks: Dict[str, TaskInfo] = {}

    def create_task(self, task_id: str, file_info: Optional[Dict] = None) -> TaskInfo:
        now = datetime.utcnow()
        task = TaskInfo(
            task_id=task_id,
            status=TaskStatus.PENDING,
            created_at=now,
            updated_at=now,
            message="Task created",
            file_info=file_info
        )
        self._tasks[task_id] = task
        return task

    def update_task(self, task_id: str, status: TaskStatus, message: str = None, error: str = None):
        if task_id in self._tasks:
            task = self._tasks[task_id]
            task.status = status
            task.updated_at = datetime.utcnow()
            if message:
                task.message = message
            if error:
                task.error = error

    def get_task(self, task_id: str) -> Optional[TaskInfo]:
        return self._tasks.get(task_id)

    def list_tasks(self) -> List[TaskInfo]:
        return list(self._tasks.values())

def get_task_manager():
    return TaskManager()
