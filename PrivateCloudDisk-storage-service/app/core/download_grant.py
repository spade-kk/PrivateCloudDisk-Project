"""
下载授权模块（Opaque Token 引擎）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
提供基于 Opaque Token 的下载授权机制，替代旧的 JWT 操作凭证。

核心设计：
  1. Token 格式：dgt_v1.{random_hex}.{hmac_hex}
     - random_hex  = secrets.token_hex(32) → 64 字符
     - hmac_hex    = HMAC-SHA256(random, secret) → 64 字符
  2. 授权数据存储在 Redis，Token 本身不携带任何业务信息
  3. 多层级并发限制（企业级）：
     L1 — 用户级（User）          ：最多 15 个活跃 Grant
     L2 — 用户+IP 级（User+IP）    ：最多 5 个活跃 Grant
     L3 — 用户+Session 级（预留）  ：最多 2 个活跃 Grant

Redis 键设计：
  download_grant:token:{token_hash}          → Hash  {userId,fileId,fileName,fileSize,
                                                      status,expiresAt,issuedAt,
                                                      maxParallelChunks,ip,sessionId}
  download_grant:user:{user_id}:active       → Set   {token_hash, ...}
  download_grant:user_ip:{user_ip_key}:active → Set   {token_hash, ...}
  download_grant:meta:{token_hash}            → String (JSON) 文件元数据缓存
  download_grant:chunk:inflight:{token_hash}  → SortedSet  分块并发控制
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"""
import hashlib
import hmac
import json
import secrets
import time
import uuid
from datetime import datetime, timezone
from typing import Optional, Tuple

from fastapi import HTTPException, status

from core.config import settings
from app.core.redis_client import redis_client
from app.core.business_service_client import business_service_client
from app.utils.helpers import stable_hash

# ============================
# 常量
# ============================

# Token 前缀
TOKEN_PREFIX = "dgt_v1"

# Redis Key 前缀
PREFIX_GRANT_TOKEN = "download_grant:token:"
PREFIX_GRANT_USER = "download_grant:user:"
PREFIX_GRANT_USER_IP = "download_grant:user_ip:"
PREFIX_GRANT_META = "download_grant:meta:"
PREFIX_CHUNK_INFLIGHT = "download_grant:chunk:inflight:"

# Grant 状态
GRANT_STATUS_ACTIVE = "ACTIVE"
GRANT_STATUS_COMPLETED = "COMPLETED"
GRANT_STATUS_CANCELLED = "CANCELLED"

# 多层级并发限制
USER_LEVEL_MAX_ACTIVE_GRANTS = 15       # L1: 用户级
USER_IP_LEVEL_MAX_ACTIVE_GRANTS = 5     # L2: 用户+IP级
USER_SESSION_MAX_ACTIVE_GRANTS = 2      # L3: 用户+Session级（预留）

# Grant 有效期（秒）
DOWNLOAD_GRANT_TTL_SECONDS = 1800       # 半小时

# 每个 Grant 最大并行分块数
MAX_PARALLEL_CHUNKS = 4

# HMAC 密钥（从 settings 读取或生成 fallback）
_DOWNLOAD_GRANT_HMAC_KEY: Optional[bytes] = None


def _get_hmac_key() -> bytes:
    """获取 HMAC 签名密钥（懒加载）"""
    global _DOWNLOAD_GRANT_HMAC_KEY
    if _DOWNLOAD_GRANT_HMAC_KEY is None:
        try:
            with open(settings.private_key_path, "rb") as f:
                _DOWNLOAD_GRANT_HMAC_KEY = hashlib.sha256(f.read()).digest()
        except Exception:
            _DOWNLOAD_GRANT_HMAC_KEY = secrets.token_bytes(32)
    return _DOWNLOAD_GRANT_HMAC_KEY


# ============================
# Token 生成与哈希
# ============================

def generate_opaque_token() -> Tuple[str, str]:
    """
    生成 Opaque Token

    格式：dgt_v1.{random_hex}.{hmac_hex}

    Returns:
        (full_token, token_hash)
        - full_token: 完整的 Token 字符串（返回给客户端）
        - token_hash: SHA-256 哈希（用作 Redis 键）
    """
    random_bytes = secrets.token_hex(32)  # 64 hex chars
    hmac_key = _get_hmac_key()
    signature = hmac.new(hmac_key, random_bytes.encode(), hashlib.sha256).hexdigest()
    full_token = f"{TOKEN_PREFIX}.{random_bytes}.{signature}"
    token_hash = _hash_token(full_token)
    return full_token, token_hash


def _hash_token(token: str) -> str:
    """对 Token 进行 SHA-256 哈希"""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def validate_token_format(token: str) -> bool:
    """
    验证 Token 格式完整性（防篡改）

    不涉及 Redis 查询，仅做格式校验。
    """
    parts = token.split(".")
    if len(parts) != 3 or parts[0] != TOKEN_PREFIX:
        return False
    hmac_key = _get_hmac_key()
    expected = hmac.new(hmac_key, parts[1].encode(), hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, parts[2])


# ============================
# Grant 颁发（多层级限流）
# ============================

async def issue_download_grant(
    user_id: str,
    file_id: str,
    client_ip: str,
    session_id: Optional[str] = None,
    ttl_seconds: int = DOWNLOAD_GRANT_TTL_SECONDS,
) -> str:
    """
    颁发下载授权 Grant（Opaque Token）

    多层级并发限制（企业级）：
      L1 用户级：最多 15 个活跃 Grant
      L2 用户+IP 级：最多 5 个活跃 Grant

    流程：
      1. 调用业务服务验证文件权限
      2. 执行 L1 用户级并发检查
      3. 执行 L2 用户+IP 级并发检查
      4. 生成 Opaque Token
      5. 将授权信息写入 Redis
      6. 更新多层级计数器
      7. 缓存文件元数据
      8. 返回 Token

    Args:
        user_id: 用户 ID
        file_id: 文件 ID
        client_ip: 客户端 IP
        session_id: 会话 ID（可选，预留 L3 层）
        ttl_seconds: Token 有效期（秒）

    Returns:
        str: Opaque Token

    Raises:
        HTTPException 429: 超过并发限制
        HTTPException 404: 文件不存在
        HTTPException 503: 业务服务不可用
    """
    # 1. 验证文件权限
    metadata = await _fetch_file_metadata(file_id, user_id)

    # 2. L1 — 用户级并发检查
    user_key = _user_active_key(user_id)
    user_active_count = await redis_client.scard(user_key)
    if user_active_count >= USER_LEVEL_MAX_ACTIVE_GRANTS:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"User-level download grant limit reached ({USER_LEVEL_MAX_ACTIVE_GRANTS} active). "
                   f"Please wait for existing downloads to complete."
        )

    # 3. L2 — 用户+IP 级并发检查
    user_ip_key = _user_ip_active_key(user_id, client_ip)
    user_ip_active_count = await redis_client.scard(user_ip_key)
    if user_ip_active_count >= USER_IP_LEVEL_MAX_ACTIVE_GRANTS:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"User+IP-level download grant limit reached ({USER_IP_LEVEL_MAX_ACTIVE_GRANTS} active). "
                   f"Please wait for existing downloads from this IP to complete."
        )

    # 4. 生成 Opaque Token
    full_token, token_hash = generate_opaque_token()
    now_ms = int(time.time() * 1000)
    expires_at = now_ms + ttl_seconds * 1000

    # 5. 写入 Redis（Hash 结构）
    grant_data = {
        "userId": user_id,
        "fileId": file_id,
        "fileName": metadata.get("name", ""),
        "fileSize": metadata.get("size", 0),
        "fileType": metadata.get("file_type", ""),
        "status": GRANT_STATUS_ACTIVE,
        "issuedAt": now_ms,
        "expiresAt": expires_at,
        "maxParallelChunks": MAX_PARALLEL_CHUNKS,
        "ip": client_ip,
        "sessionId": session_id or "",
    }
    token_key = _token_key(token_hash)
    await redis_client.hset(token_key, mapping=grant_data)
    await redis_client.expire(token_key, ttl_seconds + 60)

    # 6. 更新多层级计数器
    await redis_client.sadd(user_key, token_hash)
    await redis_client.expire(user_key, ttl_seconds + 120)
    await redis_client.sadd(user_ip_key, token_hash)
    await redis_client.expire(user_ip_key, ttl_seconds + 120)

    # 7. 缓存文件元数据
    meta_key = f"{PREFIX_GRANT_META}{token_hash}"
    await redis_client.setex(
        meta_key,
        ttl_seconds - 30,
        json.dumps({
            "storage_path": metadata.get("storage_path"),
            "file_size": metadata.get("size"),
            "file_name": metadata.get("name"),
        })
    )

    return full_token


# ============================
# Grant 验证
# ============================

async def verify_download_grant(token: str, file_id: str) -> dict:
    """
    验证下载授权 Grant

    流程：
      1. Token 格式校验
      2. Redis 查询授权信息
      3. 验证状态（ACTIVE）
      4. 验证文件 ID 匹配
      5. 验证是否过期

    Args:
        token: 下载授权 Token
        file_id: 文件 ID

    Returns:
        dict: 授权信息字典

    Raises:
        HTTPException 401: 授权无效/过期/已取消
        HTTPException 403: 文件 ID 不匹配
    """
    # 1. 格式校验
    if not validate_token_format(token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid download grant format"
        )

    token_hash = _hash_token(token)
    token_key = _token_key(token_hash)

    # 2. 从 Redis 获取授权信息
    grant_data = await redis_client.hgetall(token_key)
    if not grant_data:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Download grant not found or expired"
        )

    # 3. 验证状态
    current_status = grant_data.get("status", "")
    if current_status != GRANT_STATUS_ACTIVE:
        status_map = {
            GRANT_STATUS_COMPLETED: "Download grant already completed",
            GRANT_STATUS_CANCELLED: "Download grant has been cancelled",
        }
        detail = status_map.get(current_status, f"Download grant is {current_status}")
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=detail)

    # 4. 验证文件 ID
    if grant_data.get("fileId") != file_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Download grant not for this file"
        )

    # 5. 验证过期
    now_ms = int(time.time() * 1000)
    if now_ms > int(grant_data.get("expiresAt", 0)):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Download grant expired"
        )

    return grant_data


# ============================
# Grant 释放（下载完成）
# ============================

async def release_download_grant(token: str):
    """
    释放下载授权 Grant（下载完成后调用）

    操作：
      1. 标记状态为 COMPLETED
      2. 从多层级计数器移除
      3. 设置短 TTL 用于清理
      4. 清理分块并发计数器
      5. 清理元数据缓存
      6. 发布文件下载完成事件（MQ）

    Args:
        token: 下载授权 Token
    """
    if not validate_token_format(token):
        return

    token_hash = _hash_token(token)
    token_key = _token_key(token_hash)

    grant_data = await redis_client.hgetall(token_key)
    if not grant_data:
        return

    user_id = grant_data.get("userId", "")
    client_ip = grant_data.get("ip", "")
    file_id = grant_data.get("fileId", "")
    file_name = grant_data.get("fileName", "")
    file_size = int(grant_data.get("fileSize", 0))
    file_type = grant_data.get("fileType", "")

    # 标记为 COMPLETED，短 TTL
    await redis_client.hset(token_key, "status", GRANT_STATUS_COMPLETED)
    await redis_client.expire(token_key, 30)

    # 从多层级计数器移除
    if user_id:
        await redis_client.srem(_user_active_key(user_id), token_hash)
    if user_id and client_ip:
        await redis_client.srem(_user_ip_active_key(user_id, client_ip), token_hash)

    # 清理分块并发和缓存
    await redis_client.delete(
        f"{PREFIX_CHUNK_INFLIGHT}{token_hash}",
        f"{PREFIX_GRANT_META}{token_hash}",
    )

    # 发布文件下载完成事件（MQ）
    try:
        from core.rabbitmq import rabbitmq_service
        from core.config import settings
        import logging
        logger = logging.getLogger("app.core.download_grant")

        if rabbitmq_service.connection and not rabbitmq_service.connection.is_closed:
            event_id = str(uuid.uuid4())
            await rabbitmq_service.publish_file_event(
                settings.file_downloaded_routing_key,
                {
                    "eventId": event_id,
                    "fileId": file_id,
                    "fileName": file_name,
                    "fileSize": file_size,
                    "fileType": file_type,
                    "userId": user_id,
                    "downloadGrant": token_hash,
                    "eventTime": datetime.utcnow().isoformat(),
                },
            )
            logger.info(f"文件下载完成事件已发布: eventId={event_id}, fileId={file_id}")
        else:
            logger.warning("RabbitMQ 不可用，跳过发布下载完成事件")
    except Exception as e:
        logger.error(f"发布下载完成事件失败: {e}", exc_info=True)


# ============================
# Grant 取消
# ============================

async def cancel_download_grant(token: str, user_id: str):
    """
    取消下载授权 Grant

    验证用户身份后取消授权。

    操作：
      1. 验证 Token 存在且用户匹配
      2. 标记状态为 CANCELLED
      3. 从多层级计数器移除
      4. 设置短 TTL 用于清理
      5. 清理分块并发计数器和元数据缓存

    Args:
        token: 下载授权 Token
        user_id: 用户 ID（必须匹配）

    Raises:
        HTTPException 401: Token 无效
        HTTPException 403: 用户不匹配
    """
    if not validate_token_format(token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid download grant format"
        )

    token_hash = _hash_token(token)
    token_key = _token_key(token_hash)

    grant_data = await redis_client.hgetall(token_key)
    if not grant_data:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Download grant not found"
        )

    if grant_data.get("userId") != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Download grant user mismatch"
        )

    client_ip = grant_data.get("ip", "")

    # 标记为 CANCELLED
    await redis_client.hset(token_key, "status", GRANT_STATUS_CANCELLED)
    await redis_client.expire(token_key, 30)

    # 从多层级计数器移除
    await redis_client.srem(_user_active_key(user_id), token_hash)
    if client_ip:
        await redis_client.srem(_user_ip_active_key(user_id, client_ip), token_hash)

    # 清理分块并发和缓存
    await redis_client.delete(
        f"{PREFIX_CHUNK_INFLIGHT}{token_hash}",
        f"{PREFIX_GRANT_META}{token_hash}",
    )


# ============================
# Grant 查询
# ============================

async def get_download_grant_status(token: str) -> Optional[dict]:
    """
    查询下载授权 Grant 状态

    Args:
        token: 下载授权 Token

    Returns:
        dict | None: 授权状态信息
    """
    if not validate_token_format(token):
        return None

    token_hash = _hash_token(token)
    token_key = _token_key(token_hash)

    grant_data = await redis_client.hgetall(token_key)
    if not grant_data:
        return None

    return {
        "status": grant_data.get("status"),
        "file_id": grant_data.get("fileId"),
        "file_name": grant_data.get("fileName"),
        "file_size": int(grant_data.get("fileSize", 0)),
        "issued_at": int(grant_data.get("issuedAt", 0)),
        "expires_at": int(grant_data.get("expiresAt", 0)),
        "max_parallel_chunks": int(grant_data.get("maxParallelChunks", MAX_PARALLEL_CHUNKS)),
    }


# ============================
# 分块并发控制
# ============================

async def acquire_chunk_permit(token: str, request_id: Optional[str] = None) -> str:
    """
    申请分块并发许可证

    Args:
        token: 下载授权 Token
        request_id: 请求 ID（可选）

    Returns:
        str: 请求 ID

    Raises:
        HTTPException 429: 分块并发超限
    """
    if request_id is None:
        request_id = str(uuid.uuid4())

    token_hash = _hash_token(token)
    token_key = _token_key(token_hash)

    max_parallel = MAX_PARALLEL_CHUNKS
    grant_data = await redis_client.hgetall(token_key)
    if grant_data:
        max_parallel = int(grant_data.get("maxParallelChunks", MAX_PARALLEL_CHUNKS))

    inflight_key = f"{PREFIX_CHUNK_INFLIGHT}{token_hash}"
    now = int(time.time() * 1000)
    timeout = now + 60 * 1000  # 60 秒超时

    # 清理过期请求
    await redis_client.zremrangebyscore(inflight_key, 0, now)

    current = await redis_client.zcard(inflight_key)
    if current >= max_parallel:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Too many concurrent chunk requests for this download grant"
        )

    await redis_client.zadd(inflight_key, {request_id: timeout})
    return request_id


async def release_chunk_permit(token: str, request_id: str):
    """释放分块并发许可证"""
    token_hash = _hash_token(token)
    inflight_key = f"{PREFIX_CHUNK_INFLIGHT}{token_hash}"
    await redis_client.zrem(inflight_key, request_id)


# ============================
# 文件元数据
# ============================

async def get_cached_file_metadata(token: str) -> Optional[dict]:
    """
    获取缓存的文件元数据

    Args:
        token: 下载授权 Token

    Returns:
        dict | None: 文件元数据
    """
    token_hash = _hash_token(token)
    meta_key = f"{PREFIX_GRANT_META}{token_hash}"
    data = await redis_client.get(meta_key)
    if data:
        return json.loads(data)
    return None


async def fetch_file_metadata_from_business_service(file_id: str, user_id: str) -> dict:
    """
    从业务服务获取文件元数据

    Raises:
        HTTPException 404: 文件不存在
        HTTPException 503: 业务服务不可用
    """
    return await _fetch_file_metadata(file_id, user_id)


# ============================
# 内部辅助函数
# ============================

async def _fetch_file_metadata(file_id: str, user_id: str) -> dict:
    """通过 SDK 异步调用业务服务获取文件元数据"""
    from app.core.business_service_client import BusinessServiceError
    try:
        result = await business_service_client.get_file_metadata(file_id, user_id)
    except BusinessServiceError as e:
        if e.status_code == 404:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="文件不存在用户网盘, 或者路径目录不存在"
            )
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Business service unavailable"
        )

    return result.get("data", {})


def _token_key(token_hash: str) -> str:
    return f"{PREFIX_GRANT_TOKEN}{token_hash}"


def _user_active_key(user_id: str) -> str:
    return f"{PREFIX_GRANT_USER}{stable_hash(user_id)}:active"


def _user_ip_active_key(user_id: str, client_ip: str) -> str:
    return f"{PREFIX_GRANT_USER_IP}{stable_hash(f'{user_id}:{client_ip}')}:active"