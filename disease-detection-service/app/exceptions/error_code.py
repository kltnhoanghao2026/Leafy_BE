from enum import Enum

class ErrorCode(Enum):
    # General Errors
    UNCATEGORIZED_EXCEPTION = (9999, "Uncategorized error", 500)
    UNAUTHENTICATED = (1001, "Unauthenticated", 401)
    UNAUTHORIZED = (1002, "You do not have permission", 403)
    INTERNAL_SERVER_ERROR = (1003, "Internal server error", 500)
    BAD_REQUEST = (1004, "Bad request", 400)
    
    # Specific Errors
    INVALID_FILE_TYPE = (2001, "Invalid file type. Expected image", 400)
    EMPTY_FILE = (2002, "Empty file uploaded", 400)
    MODEL_NOT_LOADED = (2003, "AI Model not loaded", 503)
    NO_DETECTIONS = (2004, "No detections found in image", 404)
    INVALID_MODEL_TYPE = (2005, "Invalid model type specified", 400)
    DIAGNOSE_REQUEST_NOT_FOUND = (2006, "Diagnose request not found", 404)
    DIAGNOSE_RESULT_NOT_FOUND = (2007, "Diagnose result not found", 404)

    def __init__(self, code: int, message: str, status_code: int):
        self.code = code
        self.message = message
        self.status_code = status_code
