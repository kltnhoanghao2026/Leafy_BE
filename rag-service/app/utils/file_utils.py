import hashlib
from pathlib import Path
from typing import List

from fastapi import UploadFile
from werkzeug.utils import secure_filename as werkzeug_secure_filename

from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode


def sanitize_filename(filename: str) -> str:
    """Sanitize filename to prevent path traversal."""
    if not filename:
        return "unnamed_file"
    return werkzeug_secure_filename(filename)


async def validate_file_size(file: UploadFile, limit_mb: int = 20) -> None:
    """Validate file size using Content-Length header."""
    limit_bytes = limit_mb * 1024 * 1024
    content_length = file.headers.get("content-length")
    if content_length and int(content_length) > limit_bytes:
        raise AppException(
            ErrorCode.FILE_TOO_LARGE,
            f"File size exceeds the {limit_mb} MB limit.",
        )


def validate_mime_type(file: UploadFile, allowed_types: List[str]) -> None:
    """Validate file MIME type."""
    if file.content_type not in allowed_types:
        raise AppException(
            ErrorCode.UNSUPPORTED_MIME_TYPE,
            f"Unsupported file type: {file.content_type}. Allowed: {allowed_types}",
        )


async def calculate_file_hash(file_path: Path) -> str:
    """Calculate SHA-256 hash of a file on disk."""
    sha256_hash = hashlib.sha256()
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()
