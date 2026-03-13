"""
Logging configuration for the application
"""
import logging
from logging.config import dictConfig

from app.config.config import config


def setup_logger():
    """Configure application logging"""
    dictConfig(
        {
            "version": 1,
            "disable_existing_loggers": False,
            "formatters": {
                "default": {
                    "format": "%(asctime)s - %(name)s - %(levelname)s - %(message)s",
                    "datefmt": "%Y-%m-%d %H:%M:%S",
                },
                "detailed": {
                    "format": "[%(asctime)s] %(levelname)s [%(name)s:%(lineno)d] %(message)s",
                    "datefmt": "%Y-%m-%d %H:%M:%S",
                },
            },
            "handlers": {
                "console": {
                    "class": "logging.StreamHandler",
                    "level": config.LOG_LEVEL,
                    "formatter": "detailed",
                    "stream": "ext://sys.stdout",
                },
            },
            "root": {
                "level": config.LOG_LEVEL,
                "handlers": ["console"],
            },
            "loggers": {
                "app": {
                    "level": config.LOG_LEVEL,
                    "handlers": ["console"],
                    "propagate": False,
                },
                "uvicorn": {
                    "level": config.LOG_LEVEL,
                    "handlers": ["console"],
                    "propagate": False,
                },
            },
        }
    )


logger = logging.getLogger("app")
