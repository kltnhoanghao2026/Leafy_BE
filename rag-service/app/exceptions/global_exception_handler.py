import logging
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from starlette.responses import JSONResponse

from app.exceptions.app_exception import AppException

logger = logging.getLogger(__name__)


def register_exception_handlers(app: FastAPI) -> None:
    """Register all global exception handlers on the FastAPI app."""

    @app.exception_handler(AppException)
    async def app_exception_handler(request: Request, exc: AppException):
        logger.warning(
            "AppException [%s] on %s %s: %s",
            exc.error_code.name,
            request.method,
            request.url.path,
            exc.detail,
        )
        return JSONResponse(
            status_code=exc.error_code.http_status,
            content={
                "code": exc.error_code.code,
                "message": exc.detail,
                "result": None,
            },
        )

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(request: Request, exc: RequestValidationError):
        logger.warning("Validation error on %s %s: %s", request.method, request.url.path, exc.errors())
        return JSONResponse(
            status_code=422,
            content={
                "code": 4220,
                "message": "Request validation failed",
                "result": exc.errors(),
            },
        )

    @app.exception_handler(Exception)
    async def generic_exception_handler(request: Request, exc: Exception):
        # If an AppException bubbled up without being caught by its own handler
        # (e.g. raised inside a background task or Depends chain), handle it here.
        if isinstance(exc, AppException):
            return await app_exception_handler(request, exc)

        logger.error(
            "Unhandled exception on %s %s: %s",
            request.method,
            request.url.path,
            exc,
            exc_info=True,
        )
        return JSONResponse(
            status_code=500,
            content={
                "code": 5000,
                "message": f"{type(exc).__name__}: {exc}",
                "result": None,
            },
        )
