import logging
from typing import List, Optional

from fastapi import Request, status, Depends
from pydantic import BaseModel
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse

from app.exceptions.app_exception import AppException
from app.exceptions.error_code import ErrorCode
from app.dto.response.api_response import ApiResponse

logger = logging.getLogger(__name__)

class UserPrincipal(BaseModel):
    id: str
    email: str
    roles: List[str]
    jti: Optional[str] = None
    device_id: Optional[str] = None
    remaining_ttl: Optional[int] = None
    user_agent: Optional[str] = None
    request_device_id: Optional[str] = None

class SecurityContextMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, public_paths: Optional[List[str]] = None):
        super().__init__(app)
        self.public_paths = public_paths or ["/", "/health", "/v3/api-docs", "/openapi.json", "/internal/"]

    def _is_public_path(self, path: str) -> bool:
        for p in self.public_paths:
            if path == p or (p.endswith('/') and path.startswith(p)):
                return True
            if path.startswith(p) and p not in ["/", "/health"]: # Exclude exact matches from prefixing mistakenly
                return True
        return False

    async def dispatch(self, request: Request, call_next):
        path = request.url.path

        # 1. Parse Headers (mimics Spring's SecurityContextFilter)
        user_id = request.headers.get("X-User-Id")
        email = request.headers.get("X-User-Email")
        
        user_principal = None
        if user_id is not None and email is not None:
            try:
                roles_header = request.headers.get("X-User-Roles", "")
                roles = [r.strip() for r in roles_header.split(",") if r.strip()]
                
                # Check for ROLE_ prefix equivalent to Spring
                authorities = [r if r.startswith("ROLE_") else f"ROLE_{r}" for r in roles]
                if not authorities:
                    authorities = ["ROLE_USER"]

                remaining_ttl_str = request.headers.get("X-Remaining-TTL")
                remaining_ttl = int(remaining_ttl_str) if remaining_ttl_str and remaining_ttl_str.isdigit() else None

                user_principal = UserPrincipal(
                    id=user_id,
                    email=email,
                    roles=authorities,
                    jti=request.headers.get("X-JWT-Id"),
                    device_id=request.headers.get("X-Device-Id"),
                    remaining_ttl=remaining_ttl,
                    user_agent=request.headers.get("User-Agent"),
                    request_device_id=request.headers.get("X-Device-ID"),
                )
                
                # Attach to request state
                request.state.user = user_principal
                logger.debug(f"Security context set for user: {email} ({user_id}), Device: {user_principal.device_id}")
            except Exception as e:
                logger.error(f"Error setting security context: {str(e)}")

        # 2. Check Authorization (mimics Spring's SecurityConfig)
        if not self._is_public_path(path):
            if not getattr(request.state, "user", None):
                logger.warning(f"Unauthorized access to {path}. Headers received: {request.headers}")
                return JSONResponse(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    content=ApiResponse.error(
                        code=ErrorCode.UNAUTHENTICATED.code,
                        message=ErrorCode.UNAUTHENTICATED.message_key,
                        errors={"detail": "Authentication required. Missing user context headers."}
                    ).model_dump()
                )

        response = await call_next(request)
        return response

# Dependency to get current user from request state
def get_current_user(request: Request) -> UserPrincipal:
    user = getattr(request.state, "user", None)
    if not user:
        raise AppException(ErrorCode.UNAUTHENTICATED, "Not authenticated")
    return user

# Dependency to check roles
def require_roles(required_roles: List[str]):
    def role_checker(user: UserPrincipal = Depends(get_current_user)):
        user_roles = set(user.roles)
        if not any(role in user_roles or f"ROLE_{role}" in user_roles for role in required_roles):
            raise AppException(ErrorCode.UNAUTHORIZED, "Insufficient permissions")
        return user
    return role_checker
