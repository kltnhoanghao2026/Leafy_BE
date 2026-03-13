import os
from fastapi import FastAPI
from pydantic_settings import BaseSettings, SettingsConfigDict
from app.config.security import SecurityContextMiddleware

class Settings(BaseSettings):
    app_name: str = "disease-classification-service"
    server_port: int = 8088
    eureka_server: str = "http://localhost:8761/eureka/"

    model_config = SettingsConfigDict(
        env_file=os.path.join(os.path.dirname(__file__), "..", ".env"),
        extra='ignore'
    )

settings = Settings()

from app import app as refactored_app

app = refactored_app
app.title = settings.app_name
app.openapi_url = "/v3/api-docs"

# Apply Spring Security Context equivalent logic
app.add_middleware(SecurityContextMiddleware)

import logging

logger = logging.getLogger(__name__)

from fastapi import Depends
from app.config.security import get_current_user, UserPrincipal

@app.get("/diseases/test/user-info")
def test_user_info(user: UserPrincipal = Depends(get_current_user)):
    user_info = user.model_dump()
    logger.info(f"Test endpoint accessed by user: {user.email} (ID: {user.id})")
    logger.debug(f"User detailed info: {user_info}")
    
    return {
        "message": "Authentication successful",
        "user": user_info
    }
