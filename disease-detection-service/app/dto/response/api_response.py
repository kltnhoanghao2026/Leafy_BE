from typing import Any, Generic, TypeVar, Optional, Dict
from pydantic import BaseModel, Field

T = TypeVar("T")

class ApiResponse(BaseModel, Generic[T]):
    code: int = Field(default=1000, description="Response code, 1000 means success")
    message: str = Field(default="Successful", description="Response message")
    data: Optional[T] = Field(default=None, description="Response data payload")
    errors: Optional[Dict[str, str]] = Field(default=None, description="Error details if any")

    @classmethod
    def success(cls, data: Optional[T] = None) -> "ApiResponse[T]":
        return cls(code=1000, message="Successful", data=data)

    @classmethod
    def error(cls, code: int, message: str, errors: Optional[Dict[str, str]] = None) -> "ApiResponse[Any]":
        return cls(code=code, message=message, errors=errors)
