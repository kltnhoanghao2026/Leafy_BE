import os
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # App
    app_name: str = "rag-service"
    server_port: int = 8199
    eureka_server: str = "http://localhost:8761/eureka/"

    # MongoDB
    MONGODB_HOST: str = "127.0.0.1"
    MONGODB_PORT: int = 27017
    MONGODB_USERNAME: str = "root"
    MONGODB_PASSWORD: str = "rootpassword123"
    MONGODB_DATABASE_RAG: str = "leafy_rag"

    model_config = SettingsConfigDict(
        env_file=os.path.join(os.path.dirname(__file__), "..", "..", "..", ".env"),
        extra="ignore",
    )

    @property
    def MONGODB_URI(self) -> str:
        return (
            f"mongodb://{self.MONGODB_USERNAME}:{self.MONGODB_PASSWORD}"
            f"@{self.MONGODB_HOST}:{self.MONGODB_PORT}"
        )


settings = Settings()
