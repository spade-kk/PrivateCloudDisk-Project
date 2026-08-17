"""文件内容能力；物理路径由 SDK 固定，插件无法自行指定。"""

from __future__ import annotations

from .context import current_context, require_permission

_INPUT = "/workspace/input/content.bin"
_OUTPUT = "/workspace/work/output.bin"
_MAX_WRITE_BYTES = 10 * 1024 * 1024 * 1024


def _read_with_permission(permission: str, max_bytes: int | None = None) -> bytes:
    require_permission(permission)
    limit = _MAX_WRITE_BYTES if max_bytes is None else min(int(max_bytes), _MAX_WRITE_BYTES)
    if limit <= 0:
        raise ValueError("max_bytes 必须大于 0")
    with open(_INPUT, "rb") as stream:
        content = stream.read(limit + 1)
    if len(content) > limit:
        raise ValueError("文件内容超过插件读取上限")
    return content


def read(max_bytes: int | None = None) -> bytes:
    """读取当前生命周期允许访问的内容。

    激活前自动映射到暂存读取权限，激活后映射到最终内容读取权限，保留旧插件调用方式。
    """
    permission = (
        "file.content.read"
        if current_context().get("content_frozen")
        else "file.content.read_staging"
    )
    return _read_with_permission(permission, max_bytes)


def read_staging(_file_id: str | None = None, max_bytes: int | None = None) -> bytes:
    """读取合并完成、尚未计算最终哈希的暂存内容。"""
    if current_context().get("content_frozen"):
        raise PermissionError("文件已经激活，不能再读取暂存版本")
    return _read_with_permission("file.content.read_staging", max_bytes)


def write_pre_activation(
    file_id_or_content: str | bytes | bytearray | memoryview,
    content: bytes | bytearray | memoryview | None = None,
) -> None:
    """原子候选写入。

    支持 ``write_pre_activation(content)`` 与
    ``write_pre_activation(file_id, content)`` 两种 SDK 形式；file_id 只用于调用方可读性，
    真实目标始终由不可伪造的执行上下文确定。
    """
    require_permission("file.content.write_pre_activation")
    if current_context().get("content_frozen"):
        raise PermissionError("文件已经激活，原始内容不可修改")
    raw = file_id_or_content if content is None else content
    if isinstance(raw, str):
        raise TypeError("缺少候选文件内容")
    data = bytes(raw)
    if not data:
        raise ValueError("候选文件内容不能为空")
    if len(data) > _MAX_WRITE_BYTES:
        raise ValueError("候选文件内容超过插件写入上限")
    # 每个入口拥有独立工作目录；仅在入口成功后 Runtime 才接纳该输出，
    # 因此无需把 os 模块暴露给 SDK 来做重命名。
    with open(_OUTPUT, "xb") as stream:
        stream.write(data)
        stream.flush()


def write(content: bytes | bytearray | memoryview) -> None:
    """旧版 SDK 兼容别名；新插件应使用 write_pre_activation。"""
    write_pre_activation(content)
