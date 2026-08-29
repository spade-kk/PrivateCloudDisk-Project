"""网络探测：尝试出站连接，验证 network=none（5.15）。"""

import socket


def main(context):
    try:
        socket.create_connection(("192.0.2.1", 80), timeout=3).close()
        return {"connect": True}
    except Exception as exc:
        return {"connect": False, "error": type(exc).__name__}
