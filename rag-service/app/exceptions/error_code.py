from enum import Enum


class ErrorCode(Enum):
    """
    Error codes following the same namespace as the common module.

    Tuple: (internal_code, message_key, http_status)
    """

    # ── Generic ────────────────────────────────────────────────────────────────
    UNAUTHENTICATED = (1001, "error.auth.unauthenticated", 401)
    UNAUTHORIZED = (1002, "error.auth.unauthorized", 403)
    INTERNAL_ERROR = (9999, "error.sys.uncategorized", 500)

    # ── File / Ingestion ───────────────────────────────────────────────────────
    FILE_TOO_LARGE = (4101, "error.file.too.large", 413)
    UNSUPPORTED_MIME_TYPE = (4102, "error.file.unsupported.mime", 415)
    FILE_SAVE_FAILED = (4103, "error.file.save.failed", 500)

    # ── Task ─────────────────────────────────────────────────────────────────
    TASK_NOT_FOUND = (4201, "error.task.not.found", 404)

    # ── RAG Pipeline ──────────────────────────────────────────────────────────
    RAG_PIPELINE_ERROR = (4301, "error.rag.pipeline", 500)

    # ── Treatment Plan ────────────────────────────────────────────────────────
    PLAN_NOT_FOUND = (4401, "error.treatment.plan.not.found", 404)
    PLAN_ACCESS_DENIED = (4403, "error.treatment.plan.access.denied", 403)

    # ── Conversation ─────────────────────────────────────────────────────────
    CONVERSATION_NOT_FOUND = (4501, "error.conversation.not.found", 404)
    CONVERSATION_ACCESS_DENIED = (4503, "error.conversation.access.denied", 403)

    def __init__(self, code: int, message_key: str, http_status: int):
        self.code = code
        self.message_key = message_key
        self.http_status = http_status
