"""
Custom exception handlers for the API
Follows Spring Boot error response structure
"""
from fastapi import HTTPException, status


def bad_request(msg: str = "Bad request") -> HTTPException:
    """HTTP 400 Bad Request"""
    return HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST,
        detail=msg
    )


def internal_server_error(msg: str = "Internal server error") -> HTTPException:
    """HTTP 500 Internal Server Error"""
    return HTTPException(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        detail=msg
    )


def service_unavailable(msg: str = "Service unavailable") -> HTTPException:
    """HTTP 503 Service Unavailable"""
    return HTTPException(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        detail=msg
    )


def unprocessable_entity(msg: str = "Unprocessable entity") -> HTTPException:
    """HTTP 422 Unprocessable Entity"""
    return HTTPException(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        detail=msg
    )
