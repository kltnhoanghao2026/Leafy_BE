"""
File-service HTTP client — uploads files to the file-service internal endpoint.

Uses ``httpx`` for synchronous uploads to ``POST /internal/files/upload``.
The internal endpoint requires no JWT authentication.
"""

import logging
from pathlib import Path
from typing import Optional, Tuple

import httpx

from app.config.settings import settings

logger = logging.getLogger(__name__)

# Timeout: 30 seconds for connect, 120 seconds for read (large files)
_TIMEOUT = httpx.Timeout(connect=30.0, read=120.0, write=120.0, pool=30.0)


class FileServiceClient:
    """Uploads files to the file-service internal endpoint."""

    _instance: Optional["FileServiceClient"] = None

    def __new__(cls) -> "FileServiceClient":
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    @property
    def _base_url(self) -> str:
        return settings.FILE_SERVICE_URL.rstrip("/")

    def upload_file(
        self,
        file_path: Path,
        content_type: str = "application/octet-stream",
    ) -> Tuple[Optional[str], Optional[str]]:
        """Upload a file to file-service and return (file_id, s3_key).

        Parameters
        ----------
        file_path : Path
            Absolute path to the file to upload.
        content_type : str
            MIME type of the file.

        Returns
        -------
        tuple[str | None, str | None]
            ``(file_id, s3_key)`` on success, ``(None, None)`` on failure.
        """
        url = f"{self._base_url}/internal/files/upload"
        filename = file_path.name

        try:
            with open(file_path, "rb") as f:
                files = {
                    "file": (filename, f, content_type),
                }
                response = httpx.post(url, files=files, timeout=_TIMEOUT)

            if response.status_code in (200, 201):
                body = response.json()
                # ApiResponse wrapper: body.result or body.data
                result = body.get("result") or body.get("data") or {}
                file_id = result.get("id")
                s3_key = result.get("s3Key")
                logger.info(
                    "Uploaded file to file-service: file_id=%s, s3_key=%s",
                    file_id,
                    s3_key,
                )
                return file_id, s3_key
            else:
                logger.error(
                    "File-service upload failed: status=%d, body=%s",
                    response.status_code,
                    response.text[:500],
                )
                return None, None

        except httpx.ConnectError:
            logger.warning(
                "Could not connect to file-service at %s — file will not be persisted to S3",
                url,
            )
            return None, None
        except Exception as exc:
            logger.error("Unexpected error uploading to file-service: %s", exc)
            return None, None


def get_file_service_client() -> FileServiceClient:
    """Global singleton accessor."""
    return FileServiceClient()
