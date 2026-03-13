from typing import Generic, Optional, TypeVar
from pydantic import BaseModel

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    """
    Standard response envelope used by all controllers.
    Mirrors the auth-service / disease-detection-service pattern.
    """

    code: int = 200
    message: str = "Success"
    result: Optional[T] = None

    @classmethod
    def success(cls, result: T = None, message: str = "Success") -> "ApiResponse[T]":
        return cls(code=200, message=message, result=result)

    @classmethod
    def error(cls, code: int, message: str) -> "ApiResponse[None]":
        return cls(code=code, message=message, result=None)
