"""
Configuration module for Image Classification Service
Supports multiple environments: test, dev, prod
"""
from functools import cache

import os
from pydantic_settings import BaseSettings, SettingsConfigDict

_BACKEND_ENV = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    ".env",
)


class BaseConfig(BaseSettings):
    """Base configuration with environment state"""
    model_config = SettingsConfigDict(env_file=[_BACKEND_ENV, ".env"], extra="ignore")
    ENV_STATE: str = "dev"


class Config(BaseConfig):
    """Common configuration for all environments"""
    model_config = SettingsConfigDict(env_file=[_BACKEND_ENV, ".env"], extra="ignore")

    # Service settings
    SERVICE_NAME: str = "Image Classification Service"
    SERVICE_VERSION: str = "1.0.0"

    # Server settings
    HOST: str = "0.0.0.0"
    PORT: int = 8000

    # Model settings
    MODEL_NAME: str = "Coffee-MobileNetV2"
    MODEL_PATH: str = "weights/coffee_mobilenetv2_prod_final.keras"
    MODEL_INPUT_SIZE: tuple[int, int] = (224, 224)
    MODEL_TOP_K: int = 3  # Top K predictions to return

    # File Service settings
    FILE_SERVICE_URL: str = "http://localhost:8084/internal/files"

    # Logging
    LOG_LEVEL: str = "INFO"

    # TensorFlow settings
    TF_CPP_MIN_LOG_LEVEL: str = "2"  # Suppress TF warnings

    # MongoDB settings (read from backend/.env)
    MONGODB_HOST: str = "localhost"
    MONGODB_PORT: int = 27017
    MONGODB_USERNAME: str = "admin"
    MONGODB_PASSWORD: str = "admin123"
    MONGODB_DATABASE_DISEASE: str = "leafy_disease"

    @property
    def MONGODB_URI(self) -> str:
        return (
            f"mongodb://{self.MONGODB_USERNAME}:{self.MONGODB_PASSWORD}"
            f"@{self.MONGODB_HOST}:{self.MONGODB_PORT}"
        )


class TestConfig(Config):
    """Test environment configuration"""
    LOG_LEVEL: str = "DEBUG"
    ENV_STATE: str = "test"


class DevConfig(Config):
    """Development environment configuration"""
    model_config = SettingsConfigDict(
        env_file=[_BACKEND_ENV, ".env"], env_prefix="DEV_", extra="ignore"
    )
    LOG_LEVEL: str = "DEBUG"
    PORT: int = 8000


class ProdConfig(Config):
    """Production environment configuration"""
    model_config = SettingsConfigDict(
        env_file=[_BACKEND_ENV, ".env"], env_prefix="PROD_", extra="ignore"
    )
    LOG_LEVEL: str = "INFO"
    # In production, you might want to load model from a specific path
    # MODEL_PATH: str = "/app/models/mobilenetv2.keras"


@cache
def get_config(env: str = "dev") -> TestConfig | DevConfig | ProdConfig:
    """
    Get configuration based on environment

    Args:
        env: Environment name (test, dev, prod)

    Returns:
        Configuration instance for the specified environment
    """
    config_map = {
        "test": TestConfig,
        "dev": DevConfig,
        "prod": ProdConfig
    }
    return config_map[env](ENV_STATE=env)


# Global config instance
config = get_config(env=BaseConfig().ENV_STATE)

