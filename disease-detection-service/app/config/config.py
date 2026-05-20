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

    # File Service settings (from environment)
    FILE_SERVICE_URL: str = "http://file-service:8084/internal/files"

    # Logging
    LOG_LEVEL: str = "INFO"

    # TensorFlow settings
    TF_CPP_MIN_LOG_LEVEL: str = "2"  # Suppress TF warnings

    # MongoDB settings (from environment variables - no hardcoded defaults for prod)
    MONGODB_HOST: str = "mongodb"
    MONGODB_PORT: int = 27017
    MONGODB_USERNAME: str = "admin"
    MONGODB_PASSWORD: str = ""  # No default - must be set via environment
    MONGODB_DATABASE_DISEASE: str = "leafy_disease"

    @property
    def MONGODB_URI(self) -> str:
        if self.MONGODB_USERNAME and self.MONGODB_PASSWORD:
            return (
                f"mongodb://{self.MONGODB_USERNAME}:{self.MONGODB_PASSWORD}"
                f"@{self.MONGODB_HOST}:{self.MONGODB_PORT}"
            )
        return f"mongodb://{self.MONGODB_HOST}:{self.MONGODB_PORT}"


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
    # Development defaults (use docker-compose service names)
    MONGODB_HOST: str = "localhost"
    MONGODB_PASSWORD: str = "admin123"  # Only for local dev
    eureka_server: str = "http://localhost:8761/eureka/"  # Local Eureka for dev


class ProdConfig(Config):
    """Production environment configuration"""
    model_config = SettingsConfigDict(
        env_file=[_BACKEND_ENV, ".env"], env_prefix="PROD_", extra="ignore"
    )
    LOG_LEVEL: str = "WARNING"
    FILE_SERVICE_URL: str = "http://file-service:8084/internal/files"
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

