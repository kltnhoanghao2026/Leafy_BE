import uvicorn
import py_eureka_client.eureka_client as eureka_client
import os
import socket
from dotenv import load_dotenv

env_path = os.path.join(os.path.dirname(__file__), '..', '.env')
load_dotenv(env_path)

# Import app and settings AFTER dotenv is loaded
from app.main import app  # noqa: E402
from app.config.settings import settings  # noqa: E402


def _get_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.255.255.255', 1))
        return s.getsockname()[0]
    except Exception:
        return '127.0.0.1'
    finally:
        s.close()


def _get_port() -> int:
    raw = os.getenv("SERVER_PORT_RAG") or os.getenv("SERVER_PORT")
    return int(raw) if raw else settings.server_port


def _get_app_name() -> str:
    return os.getenv("SPRING_APPLICATION_NAME", settings.app_name)


def _get_eureka_server() -> str:
    return os.getenv("EUREKA_CLIENT_SERVICEURL_DEFAULTZONE", settings.eureka_server)


if __name__ == "__main__":
    port = _get_port()
    app_name = _get_app_name()
    eureka_server = _get_eureka_server()

    eureka_client.init(
        eureka_server=eureka_server,
        app_name=app_name,
        instance_port=port,
        instance_host=_get_ip(),
    )

    uvicorn.run(
        "app.main:app", 
        host="0.0.0.0", 
        port=port, 
        reload=True,
        reload_excludes=["*.db", "*.db-journal", "checkpoints.db*"]
    )
