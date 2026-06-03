from typing import Dict, Generic, Optional, TypeVar
from pydantic import BaseModel

from app.i18n import get_message

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    """
    Standard response envelope used by all controllers.
    Matches the common module's ApiResponse: { code, message, data, errors }.
    """

    code: int = 1000
    message: str = get_message("response.success", "en")
    data: Optional[T] = None
    errors: Optional[Dict[str, str]] = None

    @classmethod
    def success(
        cls,
        data: T = None,
        message: Optional[str] = None,
        locale: str = "en",
    ) -> "ApiResponse[T]":
        return cls(code=1000, message=message or get_message("response.success", locale), data=data)

    @classmethod
    def error(cls, code: int, message: str, errors: Optional[Dict[str, str]] = None) -> "ApiResponse[None]":
        return cls(code=code, message=message, data=None, errors=errors)
