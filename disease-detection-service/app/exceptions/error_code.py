from enum import Enum

class ErrorCode(Enum):
    """
    Error codes following the same namespace as the common module.

    Tuple: (internal_code, message_key, http_status)
    """
    # General Errors
    UNCATEGORIZED_EXCEPTION = (9999, "error.sys.uncategorized", 500)
    UNAUTHENTICATED = (1001, "error.auth.unauthenticated", 401)
    UNAUTHORIZED = (1002, "error.auth.unauthorized", 403)
    INTERNAL_SERVER_ERROR = (9998, "error.sys.uncategorized", 500)
    BAD_REQUEST = (2200, "error.validation.error", 400)

    # Specific Errors
    INVALID_FILE_TYPE = (2001, "error.file.invalid.type", 400)
    EMPTY_FILE = (2002, "error.file.empty", 400)
    MODEL_NOT_LOADED = (2003, "error.model.not.loaded", 503)
    NO_DETECTIONS = (2004, "error.no.detections", 404)
    INVALID_MODEL_TYPE = (2005, "error.model.invalid.type", 400)
    DIAGNOSE_REQUEST_NOT_FOUND = (2006, "error.diagnose.request.not.found", 404)
    DIAGNOSE_RESULT_NOT_FOUND = (2007, "error.diagnose.result.not.found", 404)

    def __init__(self, code: int, message_key: str, status_code: int):
        self.code = code
        self.message_key = message_key
        self.status_code = status_code
