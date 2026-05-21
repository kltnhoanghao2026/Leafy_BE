import uvicorn
import py_eureka_client.eureka_client as eureka_client
import os
from dotenv import load_dotenv

_SERVICE_DIR = os.path.dirname(os.path.abspath(__file__))
env_path = os.path.join(_SERVICE_DIR, '.env')
load_dotenv(env_path)

from main import app, settings

def get_eureka_server():
    return os.getenv("EUREKA_CLIENT_SERVICEURL_DEFAULTZONE", settings.eureka_server)

def get_server_port():
    port = os.getenv("SERVER_PORT_DISEASE_CLASSIFICATION")
    if not port:
        port = os.getenv("SERVER_PORT")
    return int(port) if port else settings.server_port

def get_app_name():
    return os.getenv("SPRING_APPLICATION_NAME", settings.app_name)

if __name__ == "__main__":
    port = get_server_port()
    app_name = get_app_name()
    eureka_server = get_eureka_server()

    # Register to Eureka
    import socket
    def get_ip():
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(('10.255.255.255', 1))
            IP = s.getsockname()[0]
        except Exception:
            IP = '127.0.0.1'
        finally:
            s.close()
        return IP

    eureka_client.init(
        eureka_server=eureka_server,
        app_name=app_name,
        instance_port=port,
        instance_host=get_ip(),
    )

    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True)
