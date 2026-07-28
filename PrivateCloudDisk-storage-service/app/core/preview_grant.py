"""
原始文件内容预览授权（有状态 Opaque Token）。

需求三-1/2/3/4：
  - 只授权 Markdown、图片、代码/纯文本源文件，不授权 HLS、压缩包树、Office 转换产物；
  - Token 仅保存哈希，完整 Token 不写日志、不落库；
  - 有效期和并发配额均严于下载授权；
  - 释放授权不会发布“文件已下载”事件，也不会写入最近访问记录。

原行为与新行为：
  原预览页申请 Download Grant 并调用下载接口，导致预览行为被误记为下载；
  新实现使用独立 pgt_v1 Token 和 preview_grant Redis 命名空间，职责完全隔离。
"""
import hashlib
import hmac
import json
import mimetypes
import secrets
import time
from pathlib import Path
from typing import Optional, Tuple

from fastapi import HTTPException, status

from app.core.business_service_client import BusinessServiceError, business_service_client
from app.core.redis_client import redis_client
from app.utils.helpers import stable_hash
from core.config import settings

TOKEN_PREFIX = "pgt_v1"
PREFIX_TOKEN = "preview_grant:token:"
PREFIX_USER = "preview_grant:user:"
PREFIX_USER_IP = "preview_grant:user_ip:"
PREFIX_META = "preview_grant:meta:"
GRANT_STATUS_ACTIVE = "ACTIVE"
GRANT_STATUS_COMPLETED = "COMPLETED"
GRANT_STATUS_CANCELLED = "CANCELLED"

# SVG/HTML 即使可显示，也可能携带主动内容；原始预览接口主动排除，代码阅读仍允许以文本方式查看 html。
IMAGE_EXTENSIONS = {
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "ico", "tif", "tiff",
}
MARKDOWN_EXTENSIONS = {"md", "markdown", "mdown", "mkd"}
TEXT_EXTENSIONS = {
    "txt", "log", "csv", "tsv", "json", "jsonl", "xml", "yaml", "yml", "toml", "ini", "conf",
    "properties", "env", "gitignore", "editorconfig",
    "js", "jsx", "mjs", "cjs", "ts", "tsx", "vue", "svelte",
    "css", "scss", "sass", "less", "html", "htm",
    "py", "pyw", "java", "kt", "kts", "go", "rs", "c", "h", "cc", "cpp", "cxx", "hpp",
    "cs", "php", "rb", "sh", "bash", "zsh", "fish", "ps1", "sql", "graphql", "gql",
    "swift", "dart", "scala", "lua", "r", "pl", "dockerfile", "makefile",
}

_HMAC_KEY: Optional[bytes] = None


def _get_hmac_key() -> bytes:
    """复用服务私钥派生签名密钥；读取失败时仅在当前进程生成临时密钥。"""
    global _HMAC_KEY
    if _HMAC_KEY is None:
        try:
            _HMAC_KEY = hashlib.sha256(Path(settings.private_key_path).read_bytes() + b":preview").digest()
        except Exception:
            _HMAC_KEY = secrets.token_bytes(32)
    return _HMAC_KEY


def _hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _generate_token() -> Tuple[str, str]:
    random_hex = secrets.token_hex(32)
    signature = hmac.new(_get_hmac_key(), random_hex.encode(), hashlib.sha256).hexdigest()
    token = f"{TOKEN_PREFIX}.{random_hex}.{signature}"
    return token, _hash_token(token)


def validate_preview_token_format(token: str) -> bool:
    parts = token.split(".")
    if len(parts) != 3 or parts[0] != TOKEN_PREFIX:
        return False
    expected = hmac.new(_get_hmac_key(), parts[1].encode(), hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, parts[2])


def _extension(file_name: str) -> str:
    name = Path(file_name).name.lower()
    if name in {"dockerfile", "makefile"}:
        return name
    return Path(name).suffix.lstrip(".")


def classify_preview_file(file_name: str, mime_type: str, file_size: int) -> tuple[str, int, str]:
    """
    返回 (preview_kind, size_limit, response_mime)；不在白名单时直接拒绝。

    扩展名是主要判定依据，MIME 仅用于响应类型，避免客户端伪造 application/octet-stream
    绕过文件类型边界；文件大小在颁发 Token 前校验，减少无效授权占用。
    """
    extension = _extension(file_name)
    normalized_mime = (mime_type or "").split(";", 1)[0].strip().lower()
    guessed_mime = mimetypes.guess_type(file_name)[0] or normalized_mime or "application/octet-stream"

    if extension in IMAGE_EXTENSIONS:
        kind, limit = "image", settings.preview_image_max_bytes
    elif extension in MARKDOWN_EXTENSIONS:
        kind, limit = "markdown", settings.preview_text_max_bytes
        guessed_mime = "text/markdown; charset=utf-8"
    elif extension in TEXT_EXTENSIONS:
        kind, limit = "text", settings.preview_text_max_bytes
        if not guessed_mime.startswith("text/"):
            guessed_mime = "text/plain; charset=utf-8"
    else:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="该文件类型不支持读取原始内容，请使用对应的专用预览或下载功能",
        )

    if file_size < 0 or file_size > limit:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"文件超过在线预览上限（{limit // 1024 // 1024}MB），请使用下载功能",
            headers={"X-Preview-Max-Size": str(limit)},
        )
    return kind, limit, guessed_mime


async def _fetch_metadata(file_id: str, user_id: str) -> dict:
    try:
        result = await business_service_client.get_file_metadata(file_id, user_id)
    except BusinessServiceError as exc:
        raise HTTPException(status_code=exc.status_code, detail="文件不存在或无权预览") from exc
    metadata = result.get("data") or {}
    if not metadata:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="文件不存在或无权预览")
    return metadata


async def _prune_active_set(key: str) -> int:
    """清除集合中已过期 Token 的哈希引用，避免异常关闭页面永久占用并发配额。"""
    members = await redis_client.smembers(key)
    if not members:
        return 0
    stale = []
    for token_hash in members:
        if not await redis_client.exists(f"{PREFIX_TOKEN}{token_hash}"):
            stale.append(token_hash)
    if stale:
        await redis_client.srem(key, *stale)
    return len(members) - len(stale)


async def issue_preview_grant(user_id: str, file_id: str, client_ip: str) -> tuple[str, dict]:
    metadata = await _fetch_metadata(file_id, user_id)
    file_name = str(metadata.get("name") or metadata.get("file_name") or "")
    file_size = int(metadata.get("size") or metadata.get("file_size") or 0)
    file_type = str(metadata.get("file_type") or metadata.get("type") or metadata.get("mime_type") or "")
    preview_kind, size_limit, response_mime = classify_preview_file(file_name, file_type, file_size)

    user_key = f"{PREFIX_USER}{stable_hash(user_id)}:active"
    user_ip_key = f"{PREFIX_USER_IP}{stable_hash(f'{user_id}:{client_ip}')}:active"
    if await _prune_active_set(user_key) >= settings.preview_grant_user_max_active:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="同时预览的文件过多，请关闭部分预览页后重试",
            headers={"Retry-After": "3"},
        )
    if await _prune_active_set(user_ip_key) >= settings.preview_grant_user_ip_max_active:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="当前网络同时预览的文件过多，请稍后重试",
            headers={"Retry-After": "3"},
        )

    token, token_hash = _generate_token()
    ttl = settings.preview_grant_ttl_seconds
    now_ms = int(time.time() * 1000)
    grant_data = {
        "userId": user_id,
        "fileId": file_id,
        "fileName": file_name,
        "fileSize": file_size,
        "fileType": file_type,
        "previewKind": preview_kind,
        "responseMime": response_mime,
        "sizeLimit": size_limit,
        "status": GRANT_STATUS_ACTIVE,
        "issuedAt": now_ms,
        "expiresAt": now_ms + ttl * 1000,
        "ip": client_ip,
    }
    await redis_client.hset(f"{PREFIX_TOKEN}{token_hash}", mapping=grant_data)
    await redis_client.expire(f"{PREFIX_TOKEN}{token_hash}", ttl + 30)
    await redis_client.sadd(user_key, token_hash)
    await redis_client.expire(user_key, ttl + 60)
    await redis_client.sadd(user_ip_key, token_hash)
    await redis_client.expire(user_ip_key, ttl + 60)
    await redis_client.setex(
        f"{PREFIX_META}{token_hash}",
        ttl,
        json.dumps({
            "storage_path": metadata.get("storage_path"),
            "file_size": file_size,
            "file_name": file_name,
            "response_mime": response_mime,
        }, ensure_ascii=False),
    )
    return token, grant_data


async def verify_preview_grant(token: str, user_id: str, file_id: str) -> dict:
    if not validate_preview_token_format(token):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="预览授权格式无效")
    token_hash = _hash_token(token)
    grant = await redis_client.hgetall(f"{PREFIX_TOKEN}{token_hash}")
    if not grant or grant.get("status") != GRANT_STATUS_ACTIVE:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="预览授权已过期，请刷新后重试")
    if grant.get("userId") != user_id or grant.get("fileId") != file_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="预览授权与当前用户或文件不匹配")
    if int(grant.get("expiresAt") or 0) < int(time.time() * 1000):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="预览授权已过期，请刷新后重试")
    grant["token_hash"] = token_hash
    return grant


async def get_preview_metadata(token: str) -> Optional[dict]:
    if not validate_preview_token_format(token):
        return None
    raw = await redis_client.get(f"{PREFIX_META}{_hash_token(token)}")
    return json.loads(raw) if raw else None


async def release_preview_grant(token: str, user_id: Optional[str] = None) -> None:
    """释放配额；与下载授权不同，本方法不发布下载完成事件。"""
    if not validate_preview_token_format(token):
        return
    token_hash = _hash_token(token)
    token_key = f"{PREFIX_TOKEN}{token_hash}"
    grant = await redis_client.hgetall(token_key)
    if not grant:
        return
    if user_id and grant.get("userId") != user_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="预览授权用户不匹配")
    owner = grant.get("userId", "")
    client_ip = grant.get("ip", "")
    await redis_client.hset(token_key, "status", GRANT_STATUS_COMPLETED)
    await redis_client.expire(token_key, 15)
    if owner:
        await redis_client.srem(f"{PREFIX_USER}{stable_hash(owner)}:active", token_hash)
    if owner and client_ip:
        await redis_client.srem(f"{PREFIX_USER_IP}{stable_hash(f'{owner}:{client_ip}')}:active", token_hash)
    await redis_client.delete(
        f"{PREFIX_META}{token_hash}",
        f"total:preview_grant:{token_hash}",
        f"concurrency:preview_grant:{token_hash}",
    )
