import os
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # App
    app_name: str = "rag-service"
    server_port: int = 8199
    eureka_server: str = "http://discovery-server:8761/eureka/"
    api_gateway_url: str = "http://api-gateway:8080"
    env_lookup_timeout_seconds: float = 5.0

    # Document ingestion
    # "fast" = pdfminer (no system deps), "hi_res" = Poppler + Tesseract (Docker)
    UNSTRUCTURED_STRATEGY: str = "fast"
    # Fixed-size chunking parameters (characters, not BPE tokens)
    CHUNK_SIZE: int = 1000
    CHUNK_OVERLAP: int = 200
    # Markdown header-based chunking
    MARKDOWN_CHUNK_SIZE: int = 2000
    MARKDOWN_CHUNK_OVERLAP: int = 300

    # File-service (internal endpoint — no JWT required)
    FILE_SERVICE_URL: str = "http://file-service:8084"

    # MongoDB (from environment variables - no hardcoded defaults for prod)
    MONGODB_HOST: str = "mongodb"
    MONGODB_PORT: int = 27017
    MONGODB_USERNAME: str = "admin"
    MONGODB_PASSWORD: str = ""  # No default - must be set via environment
    MONGODB_DATABASE_RAG: str = "leafy_rag"

    model_config = SettingsConfigDict(
        env_file=os.path.join(os.path.dirname(__file__), "..", "..", ".env"),
        extra="ignore",
    )

    @property
    def MONGODB_URI(self) -> str:
        if self.MONGODB_USERNAME and self.MONGODB_PASSWORD:
            return (
                f"mongodb://{self.MONGODB_USERNAME}:{self.MONGODB_PASSWORD}"
                f"@{self.MONGODB_HOST}:{self.MONGODB_PORT}"
            )
        return f"mongodb://{self.MONGODB_HOST}:{self.MONGODB_PORT}"


settings = Settings()
