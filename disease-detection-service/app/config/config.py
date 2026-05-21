"""
Configuration module for Disease Detection Service.
Loads from .env file in the service root directory.
"""
import ast
import os
from functools import cache

from dotenv import load_dotenv
from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# Load .env into os.environ before pydantic_settings processes anything
_env_path = os.path.join(os.path.dirname(__file__), "..", "..", ".env")
load_dotenv(_env_path)


class Config(BaseSettings):
    """Configuration loaded from .env file."""
    model_config = SettingsConfigDict(
        extra="ignore",
    )

    # Service settings
    SERVICE_NAME: str = "Disease Detection Service"
    SERVICE_VERSION: str = "1.0.0"
    ENV_STATE: str = "dev"

    # Server settings
    HOST: str = "0.0.0.0"
    PORT: int = 8090

    # Eureka
    EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: str = "http://localhost:8761/eureka/"
    SPRING_APPLICATION_NAME: str = "disease-classification-service"

    # Model settings
    MODEL_NAME: str = "Coffee-MobileNetV2"
    MODEL_PATH: str = "weights/coffee_mobilenetv2_prod_final.keras"
    MODEL_INPUT_SIZE: tuple[int, int] = (224, 224)
    MODEL_TOP_K: int = 3

    # YOLO model settings
    YOLO_MODEL_PATH: str = "weights/yolo/best.pt"
    YOLO_CLASS_NAMES: list[str] = [
        "healthy", "leaf_miner", "phoma", "red_spider_mite", "rust"
    ]

    # File Service settings
    FILE_SERVICE_URL: str = "http://file-service:8084/internal/files"

    # Logging
    LOG_LEVEL: str = "INFO"
    TF_CPP_MIN_LOG_LEVEL: str = "2"

    # MongoDB settings
    MONGODB_HOST: str = "mongodb"
    MONGODB_PORT: int = 27017
    MONGODB_USERNAME: str = "admin"
    MONGODB_PASSWORD: str = ""
    MONGODB_DATABASE_DISEASE: str = "leafy_disease"

    @field_validator("MODEL_INPUT_SIZE", mode="before")
    @classmethod
    def parse_model_input_size(cls, v):
        if isinstance(v, str):
            if not v.strip():
                return (224, 224)
            import json
            return tuple(json.loads(v))
        return v

    @field_validator("YOLO_CLASS_NAMES", mode="before")
    @classmethod
    def parse_yolo_class_names(cls, v):
        if isinstance(v, str):
            if not v.strip():
                return ["healthy", "leaf_miner", "phoma", "red_spider_mite", "rust"]
            return ast.literal_eval(v)
        return v

    @property
    def MONGODB_URI(self) -> str:
        if self.MONGODB_USERNAME and self.MONGODB_PASSWORD:
            return (
                f"mongodb://{self.MONGODB_USERNAME}:{self.MONGODB_PASSWORD}"
                f"@{self.MONGODB_HOST}:{self.MONGODB_PORT}"
            )
        return f"mongodb://{self.MONGODB_HOST}:{self.MONGODB_PORT}"


@cache
def get_config() -> Config:
    return Config()


config = get_config()
