"""
Exception helper functions that raise AppException with the appropriate ErrorCode.
"""
from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode


def bad_request(detail: str = None) -> AppException:
    """HTTP 400 Bad Request"""
    return AppException(ErrorCode.BAD_REQUEST, detail)


def internal_server_error(detail: str = None) -> AppException:
    """HTTP 500 Internal Server Error"""
    return AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, detail)


def service_unavailable(detail: str = None) -> AppException:
    """HTTP 503 Service Unavailable"""
    return AppException(ErrorCode.MODEL_NOT_LOADED, detail)


def unprocessable_entity(detail: str = None) -> AppException:
    """HTTP 422 Unprocessable Entity"""
    return AppException(ErrorCode.BAD_REQUEST, detail)
