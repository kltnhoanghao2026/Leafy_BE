import logging
from typing import List, Optional

from fastapi import Request, HTTPException, status, Depends
from pydantic import BaseModel
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse

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
    """
    Mirrors Spring Security's SecurityContextFilter.
    Reads X-User-* headers forwarded by the API Gateway and attaches a
    UserPrincipal to request.state.user for downstream use.
    """

    PUBLIC_PATHS = ["/", "/health", "/v3/api-docs", "/openapi.json", "/docs", "/redoc"]

    def __init__(self, app, public_paths: Optional[List[str]] = None):
        super().__init__(app)
        self.public_paths = public_paths or self.PUBLIC_PATHS

    def _is_public_path(self, path: str) -> bool:
        for p in self.public_paths:
            if path == p:
                return True
            if p.endswith("/") and path.startswith(p):
                return True
        return False

    async def dispatch(self, request: Request, call_next):
        path = request.url.path

        user_id = request.headers.get("X-User-Id")
        email = request.headers.get("X-User-Email")

        if user_id and email:
            try:
                roles_header = request.headers.get("X-User-Roles", "")
                roles = [r.strip() for r in roles_header.split(",") if r.strip()]
                authorities = [r if r.startswith("ROLE_") else f"ROLE_{r}" for r in roles]
                if not authorities:
                    authorities = ["ROLE_USER"]

                remaining_ttl_str = request.headers.get("X-Remaining-TTL")
                remaining_ttl = (
                    int(remaining_ttl_str)
                    if remaining_ttl_str and remaining_ttl_str.isdigit()
                    else None
                )

                request.state.user = UserPrincipal(
                    id=user_id,
                    email=email,
                    roles=authorities,
                    jti=request.headers.get("X-JWT-Id"),
                    device_id=request.headers.get("X-Device-Id"),
                    remaining_ttl=remaining_ttl,
                    user_agent=request.headers.get("User-Agent"),
                    request_device_id=request.headers.get("X-Device-ID"),
                )
                logger.debug("Security context set for user: %s (%s)", email, user_id)
            except Exception as e:
                logger.error("Error setting security context: %s", e)

        if not self._is_public_path(path):
            if not getattr(request.state, "user", None):
                logger.warning("Unauthorized access to %s", path)
                return JSONResponse(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    content={
                        "code": 4001,
                        "message": "Authentication required. Missing user context headers.",
                        "result": None,
                    },
                )

        return await call_next(request)


def get_current_user(request: Request) -> UserPrincipal:
    """FastAPI dependency — returns the UserPrincipal from request state."""
    user = getattr(request.state, "user", None)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Not authenticated",
        )
    return user


def require_roles(required_roles: List[str]):
    """FastAPI dependency factory — checks that the user has at least one required role."""
    def role_checker(user: UserPrincipal = Depends(get_current_user)):
        user_roles = set(user.roles)
        if not any(
            role in user_roles or f"ROLE_{role}" in user_roles for role in required_roles
        ):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Insufficient permissions",
            )
        return user
    return role_checker
