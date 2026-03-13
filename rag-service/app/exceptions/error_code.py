from enum import Enum


class ErrorCode(Enum):
    """
    Error codes following the same pattern as auth-service / disease-detection-service.

    Tuple: (internal_code, message, http_status)
    """

    # ── Generic ────────────────────────────────────────────────────────────────
    UNAUTHENTICATED = (4001, "Authentication required", 401)
    UNAUTHORIZED = (4003, "Insufficient permissions", 403)
    INTERNAL_ERROR = (5000, "An unexpected error occurred", 500)

    # ── File / Ingestion ───────────────────────────────────────────────────────
    FILE_TOO_LARGE = (4101, "File exceeds the 20 MB size limit", 413)
    UNSUPPORTED_MIME_TYPE = (4102, "Unsupported file type", 415)
    FILE_SAVE_FAILED = (4103, "Failed to save the uploaded file", 500)

    # ── Task ─────────────────────────────────────────────────────────────────
    TASK_NOT_FOUND = (4201, "Task not found", 404)

    # ── RAG Pipeline ──────────────────────────────────────────────────────────
    RAG_PIPELINE_ERROR = (4301, "RAG pipeline execution failed", 500)

    # ── Treatment Plan ────────────────────────────────────────────────────────
    TREATMENT_PLAN_NOT_FOUND = (4401, "Treatment plan not found", 404)
    TREATMENT_PLAN_ACCESS_DENIED = (4403, "Access to treatment plan denied", 403)

    def __init__(self, code: int, message: str, http_status: int):
        self.code = code
        self.message = message
        self.http_status = http_status
