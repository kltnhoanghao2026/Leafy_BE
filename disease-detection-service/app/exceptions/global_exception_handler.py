import logging
from fastapi import Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode
from app.dto.response.api_response import ApiResponse

logger = logging.getLogger(__name__)

async def app_exception_handler(request: Request, exc: AppException) -> JSONResponse:
    """Handle custom AppException and wrap in ApiResponse"""
    response = ApiResponse.error(
        code=exc.code,
        message=exc.detail
    )
    return JSONResponse(
        status_code=exc.status_code,
        content=response.model_dump(exclude_none=True)
    )

async def http_exception_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
    """Handle standard HTTP exceptions"""
    if exc.status_code == 404:
        error_code = ErrorCode.UNCATEGORIZED_EXCEPTION
    elif exc.status_code == 400:
        error_code = ErrorCode.BAD_REQUEST
    elif exc.status_code == 401:
         error_code = ErrorCode.UNAUTHENTICATED
    elif exc.status_code == 403:
         error_code = ErrorCode.UNAUTHORIZED
    else:
        error_code = ErrorCode.UNCATEGORIZED_EXCEPTION

    response = ApiResponse.error(
        code=error_code.code,
        message=str(exc.detail) if exc.detail else error_code.message_key
    )
    return JSONResponse(
        status_code=exc.status_code,
        content=response.model_dump(exclude_none=True)
    )

async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    """Handle FastAPI validation exceptions"""
    errors = { ".".join(str(loc) for loc in err["loc"]): err["msg"] for err in exc.errors() }

    response = ApiResponse.error(
        code=ErrorCode.BAD_REQUEST.code,
        message=ErrorCode.BAD_REQUEST.message_key,
        errors=errors
    )
    return JSONResponse(
        status_code=ErrorCode.BAD_REQUEST.status_code,
        content=response.model_dump(exclude_none=True)
    )

async def general_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    """Handle all other unhandled exceptions"""
    logger.error(f"Unhandled exception: {exc}", exc_info=True)

    response = ApiResponse.error(
        code=ErrorCode.UNCATEGORIZED_EXCEPTION.code,
        message=ErrorCode.UNCATEGORIZED_EXCEPTION.message_key
    )
    return JSONResponse(
        status_code=ErrorCode.UNCATEGORIZED_EXCEPTION.status_code,
        content=response.model_dump(exclude_none=True)
    )
