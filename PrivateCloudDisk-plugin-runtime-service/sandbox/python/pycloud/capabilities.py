"""Plugin capability SDK over an instance-exclusive Unix Domain Socket.

``call_api`` remains backward compatible for plugin authors. Its transport is
the fixed ``/runtime/runtime.sock`` bind mount; the Runtime Agent, not this
SDK, owns tenant identity, authorization forwarding and audit facts.

The protobuf schema is ``proto/capability_socket.proto``. This bounded wire
implementation deliberately uses only Python's standard library, so the
restricted Python image does not gain a broad third-party dependency. It does
not expose a raw socket API and never writes capability-audit.jsonl.
"""

from __future__ import annotations

import json
import socket
import struct
import threading
import time
import uuid

from .context import require_permission

_SOCKET_PATH = "/runtime/runtime.sock"
_DEFAULT_TIMEOUT = 20.0
_MAX_FRAME_BYTES = 1024 * 1024
_PERMISSION = "platform.capability.invoke"
_MAX_RETRIES = 2

_instance_id: str | None = None
_instance_token: bytes | None = None
_connections = threading.local()


class CapabilityError(Exception):
    """能力调用被拒绝或 Runtime/Capability Hub 返回了结构化失败。"""

    def __init__(self, code: str, message: str):
        super().__init__(f"[{code}] {message}")
        self.code = code
        self.message = message


class CapabilityTimeout(TimeoutError):
    """Runtime Agent 或能力中心未在受控时限内返回。"""


def _configure_runtime_transport(instance_id: str, instance_token: str) -> None:
    """仅由受信 runner.py 的 CLI 参数调用，插件代码不可配置或读取凭据。

    [CF-PLUGIN-UDS-001] token 不经环境变量、context.json 或共享挂载文件，只留在
    runner 参数与 SDK 私有内存中，并随每个插件实例轮换。
    """
    global _instance_id, _instance_token
    if not isinstance(instance_id, str) or len(instance_id) < 24:
        raise RuntimeError("Runtime 插件实例标识无效")
    if not isinstance(instance_token, str) or len(instance_token) < 48:
        raise RuntimeError("Runtime 插件实例凭据无效")
    _instance_id = instance_id
    _instance_token = instance_token.encode("utf-8")


def call_api(capability_key: str, parameters: dict | None = None, *, timeout: float = _DEFAULT_TIMEOUT) -> dict:
    """调用平台能力；不会回退到 TCP 或文件轮询。"""
    require_permission(_PERMISSION)
    if not isinstance(capability_key, str) or not capability_key:
        raise CapabilityError("CAPABILITY_REQUEST_INVALID", "能力键必须是非空字符串")
    if not isinstance(parameters, (dict, type(None))):
        raise CapabilityError("CAPABILITY_REQUEST_INVALID", "能力参数必须是对象")
    if _instance_id is None or _instance_token is None:
        raise CapabilityError("RUNTIME_INSTANCE_AUTH_FAILED", "插件实例通信凭据不可用")
    try:
        encoded_parameters = json.dumps(parameters or {}, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    except (TypeError, ValueError) as exception:
        raise CapabilityError("CAPABILITY_REQUEST_INVALID", "能力参数不可序列化为 JSON") from exception
    if len(encoded_parameters) > _MAX_FRAME_BYTES // 2:
        raise CapabilityError("CAPABILITY_REQUEST_TOO_LARGE", "能力参数超过 Socket 消息上限")
    request_id = uuid.uuid4().hex
    message = _encode_request(request_id, capability_key, encoded_parameters, _instance_id, _instance_token)
    deadline = time.monotonic() + max(0.1, float(timeout))
    last_error: Exception | None = None
    for attempt in range(_MAX_RETRIES):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        try:
            response = _exchange(message, remaining)
            return _decode_success(response, request_id)
        except CapabilityError:
            raise
        except (OSError, ValueError, struct.error) as exception:
            last_error = exception
            _drop_connection()
    if time.monotonic() >= deadline:
        raise CapabilityTimeout("平台能力调用超时") from last_error
    raise CapabilityError("RUNTIME_SOCKET_UNAVAILABLE", "Runtime Agent Unix Socket 不可用") from last_error


def _exchange(message: bytes, timeout: float) -> bytes:
    connection = _connection(timeout)
    connection.sendall(struct.pack(">I", len(message)) + message)
    header = _read_exact(connection, 4)
    length = struct.unpack(">I", header)[0]
    if length == 0 or length > _MAX_FRAME_BYTES:
        raise ValueError("RUNTIME_SOCKET_FRAME_TOO_LARGE")
    return _read_exact(connection, length)


def _connection(timeout: float) -> socket.socket:
    connection = getattr(_connections, "socket", None)
    if connection is not None:
        connection.settimeout(timeout)
        return connection
    # The restricted audit hook permits only this AF_UNIX target. User raw
    # socket imports/private SDK attributes are separately denied.
    connection = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    try:
        connection.settimeout(timeout)
        connection.connect(_SOCKET_PATH)
    except BaseException:
        connection.close()
        raise
    _connections.socket = connection
    return connection


def _drop_connection() -> None:
    connection = getattr(_connections, "socket", None)
    _connections.socket = None
    if connection is not None:
        try:
            connection.close()
        except OSError:
            pass


def _read_exact(connection: socket.socket, size: int) -> bytes:
    chunks: list[bytes] = []
    remaining = size
    while remaining:
        chunk = connection.recv(remaining)
        if not chunk:
            raise OSError("Runtime Socket closed before response completed")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def _decode_success(payload: bytes, expected_request_id: str) -> dict:
    fields = _decode_fields(payload)
    request_id = _field_text(fields, 1)
    status = _field_text(fields, 2)
    if request_id != expected_request_id or not status:
        raise CapabilityError("CAPABILITY_RESPONSE_INVALID", "Runtime 响应与请求不匹配")
    if status != "SUCCESS":
        code, message, _ = _decode_error(fields.get(4, b""))
        raise CapabilityError(code or "CAPABILITY_FAILED", message or "平台能力调用失败")
    raw_result = fields.get(3, b"{}")
    try:
        result = json.loads(raw_result.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise CapabilityError("CAPABILITY_RESPONSE_INVALID", "平台能力响应不是有效 JSON") from exception
    if result is None:
        return {}
    if not isinstance(result, dict):
        raise CapabilityError("CAPABILITY_RESPONSE_INVALID", "平台能力输出必须是对象")
    return result


def _encode_request(request_id: str, capability_key: str, parameters: bytes, instance_id: str, token: bytes) -> bytes:
    return b"".join((
        _field_bytes(1, request_id.encode("utf-8")),
        _field_bytes(2, capability_key.encode("utf-8")),
        _field_bytes(3, parameters),
        _field_bytes(4, instance_id.encode("utf-8")),
        _field_bytes(5, token),
    ))


def _field_bytes(number: int, value: bytes) -> bytes:
    return _varint((number << 3) | 2) + _varint(len(value)) + value


def _varint(value: int) -> bytes:
    output = bytearray()
    while value > 0x7F:
        output.append((value & 0x7F) | 0x80)
        value >>= 7
    output.append(value)
    return bytes(output)


def _decode_fields(payload: bytes) -> dict[int, bytes]:
    offset = 0
    fields: dict[int, bytes] = {}
    while offset < len(payload):
        tag, offset = _consume_varint(payload, offset)
        number, wire_type = tag >> 3, tag & 7
        if wire_type != 2 or number <= 0:
            raise ValueError("RUNTIME_SOCKET_PROTOCOL_INVALID")
        size, offset = _consume_varint(payload, offset)
        end = offset + size
        if end > len(payload):
            raise ValueError("RUNTIME_SOCKET_PROTOCOL_INVALID")
        fields[number] = payload[offset:end]
        offset = end
    return fields


def _decode_error(payload: bytes) -> tuple[str, str, bool]:
    if not payload:
        return "", "", False
    offset = 0
    code = message = ""
    retryable = False
    while offset < len(payload):
        tag, offset = _consume_varint(payload, offset)
        number, wire_type = tag >> 3, tag & 7
        if number in (1, 2):
            if wire_type != 2:
                raise ValueError("RUNTIME_SOCKET_PROTOCOL_INVALID")
            size, offset = _consume_varint(payload, offset)
            end = offset + size
            if end > len(payload):
                raise ValueError("RUNTIME_SOCKET_PROTOCOL_INVALID")
            value = payload[offset:end].decode("utf-8")
            offset = end
            if number == 1:
                code = value
            else:
                message = value
        elif number == 3:
            if wire_type != 0:
                raise ValueError("RUNTIME_SOCKET_PROTOCOL_INVALID")
            value, offset = _consume_varint(payload, offset)
            retryable = value != 0
        else:
            raise ValueError("RUNTIME_SOCKET_PROTOCOL_INVALID")
    return code, message, retryable


def _consume_varint(payload: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while offset < len(payload) and shift < 64:
        byte = payload[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, offset
        shift += 7
    raise ValueError("RUNTIME_SOCKET_PROTOCOL_INVALID")


def _field_text(fields: dict[int, bytes], number: int) -> str:
    try:
        return fields.get(number, b"").decode("utf-8")
    except UnicodeDecodeError as exception:
        raise CapabilityError("CAPABILITY_RESPONSE_INVALID", "Runtime 响应字符串无效") from exception


def user_info(user_id: str | None = None) -> dict:
    return call_api("api.user.info", {"user_id": user_id})


def space_members_list(space_id: str | None = None) -> dict:
    return call_api("api.space.members.list", {"space_id": space_id})


def notification_send(user_ids, message: str, *, timeout: float = _DEFAULT_TIMEOUT) -> dict:
    if isinstance(user_ids, str):
        user_ids = [user_ids]
    if not message:
        raise CapabilityError("CAPABILITY_REQUEST_INVALID", "通知消息不能为空")
    return call_api("api.notification.send", {"user_ids": list(user_ids), "message": str(message)[:2000]}, timeout=timeout)
