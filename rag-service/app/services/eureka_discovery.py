import logging
import random
from typing import Optional

import httpx

from app.config.settings import settings

logger = logging.getLogger(__name__)


def _normalise_app_name(app_name: str) -> str:
    return (app_name or "").strip().upper()


def _pick_instance(eureka_app: dict) -> Optional[dict]:
    instance = eureka_app.get("instance") if isinstance(eureka_app, dict) else None
    if instance is None:
        return None

    instances = instance if isinstance(instance, list) else [instance]

    # Keep only UP instances
    up_instances = []
    for inst in instances:
        try:
            status = inst.get("status")
            if status and str(status).upper() != "UP":
                continue
            up_instances.append(inst)
        except Exception:
            continue

    candidates = up_instances or instances
    if not candidates:
        return None

    return random.choice(candidates)


def resolve_service_base_url(app_name: str, *, prefer_ip: bool = True) -> Optional[str]:
    """Resolve a service base URL from Eureka.

    Returns something like "http://10.0.0.12:8080".

    Notes:
    - Eureka payload shape differs slightly by server/version; we handle common shapes.
    - This is a lightweight client-side discovery (no caching). You may want caching
      in high-QPS paths.
    """

    app = _normalise_app_name(app_name)
    if not app:
        return None

    eureka = settings.eureka_server.rstrip("/")
    url = f"{eureka}/apps/{app}"

    try:
        resp = httpx.get(url, headers={"Accept": "application/json"}, timeout=5.0)
        resp.raise_for_status()
        payload = resp.json()
    except Exception as exc:
        logger.warning("[EUREKA] Failed to query %s: %s", url, exc)
        return None

    # Typical: {"application": {"name": "APP", "instance": [...]}}
    eureka_app = payload.get("application") if isinstance(payload, dict) else None
    if not isinstance(eureka_app, dict):
        logger.warning("[EUREKA] Unexpected payload shape for %s", app)
        return None

    inst = _pick_instance(eureka_app)
    if not inst:
        logger.warning("[EUREKA] No instances found for %s", app)
        return None

    host = None
    port = None

    if prefer_ip:
        host = inst.get("ipAddr") or inst.get("hostName")
    else:
        host = inst.get("hostName") or inst.get("ipAddr")

    port_obj = inst.get("port")
    if isinstance(port_obj, dict):
        port = port_obj.get("$") or port_obj.get("@enabled")
        # Some servers: {"$": 8080, "@enabled": "true"}
        if isinstance(port, str) and port.isdigit():
            port = int(port)
        elif isinstance(port, int):
            pass
        else:
            port = port_obj.get("$")
    elif isinstance(port_obj, int):
        port = port_obj
    elif isinstance(port_obj, str) and port_obj.isdigit():
        port = int(port_obj)

    if not host or not port:
        logger.warning("[EUREKA] Missing host/port for %s instance=%s", app, inst)
        return None

    return f"http://{host}:{port}"
