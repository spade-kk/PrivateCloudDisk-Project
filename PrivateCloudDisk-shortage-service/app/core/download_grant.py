"""
下载授权验证模块
提供 Opaque Token + Redis Download Grant 模式的下载授权验证
"""
import uuid
import json
import hashlib
import requests
from fastapi import HTTPException, status, Header, Request, Depends
from typing import Optional

from core.config import settings
from app.core.redis_client import redis_client


# Redis Key 前缀常量
PREFIX_GRANT = "download:grant:"
PREFIX_CHUNK_INFLIGHT = "download:chunk:inflight:"

# 业务服务地址
BUSINESS_SERVICE_URL = settings.business_service_url


def hash_token(token: str) -> str:
    """
    对下载token进行SHA-256哈希处理
    
    Args:
        token: 原始下载token
        
    Returns:
        str: Base64URL编码的哈希值
    """
    digest = hashlib.sha256(token.encode('utf-8')).digest()
    return digest.hex()


async def verify_download_grant(token: str, file_id: str) -> dict:
    """
    验证下载授权
    
    验证流程：
    1. 对token进行哈希处理
    2. 从Redis查询授权信息
    3. 验证授权状态和文件ID匹配
    4. 检查过期时间
    
    Args:
        token: 下载token
        file_id: 文件ID
        
    Returns:
        dict: 授权信息字典
        
    Raises:
        HTTPException:
            - 401: 授权不存在、已过期或已撤销
            - 403: 文件ID不匹配
    """
    token_hash = hash_token(token)
    grant_key = PREFIX_GRANT + token_hash
    
    # 从Redis获取授权信息
    grant_json = await redis_client.get(grant_key)
    if not grant_json:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Download grant not found or expired"
        )
    
    try:
        grant_info = json.loads(grant_json)
    except json.JSONDecodeError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid download grant"
        )
    
    # 验证状态
    if grant_info.get("status") != "ACTIVE":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Download grant {grant_info.get('status', 'invalid')}"
        )
    
    # 验证文件ID匹配
    if grant_info.get("fileId") != file_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Download grant not for this file"
        )
    
    # 验证过期时间
    import time
    if time.time() * 1000 > grant_info.get("expiresAt", 0):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Download grant expired"
        )
    
    return grant_info


async def acquire_chunk_permit(token: str, request_id: Optional[str] = None) -> str:
    """
    申请分块并发许可证
    
    Args:
        token: 下载token
        request_id: 请求ID（可选，自动生成）
        
    Returns:
        str: 请求ID
        
    Raises:
        HTTPException:
            - 429: 分块并发数超限
    """
    if request_id is None:
        request_id = str(uuid.uuid4())
    
    token_hash = hash_token(token)
    inflight_key = PREFIX_CHUNK_INFLIGHT + token_hash
    
    # 获取当前并发数限制（从授权信息中获取）
    grant_key = PREFIX_GRANT + token_hash
    grant_json = await redis_client.get(grant_key)
    if not grant_json:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Download grant not found"
        )
    
    try:
        grant_info = json.loads(grant_json)
        max_parallel = grant_info.get("maxParallelChunks", 4)
    except json.JSONDecodeError:
        max_parallel = 4
    
    import time
    now = time.time() * 1000
    timeout = now + 60 * 1000  # 60秒超时
    
    # 清理过期的请求
    await redis_client.zremrangebyscore(inflight_key, 0, now)
    
    # 检查当前并发数
    current_count = await redis_client.zcard(inflight_key)
    if current_count >= max_parallel:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Too many concurrent chunk requests"
        )
    
    # 添加新请求
    await redis_client.zadd(inflight_key, {request_id: timeout})
    
    return request_id


async def release_chunk_permit(token: str, request_id: str):
    """
    释放分块并发许可证
    
    Args:
        token: 下载token
        request_id: 请求ID
    """
    token_hash = hash_token(token)
    inflight_key = PREFIX_CHUNK_INFLIGHT + token_hash
    await redis_client.zrem(inflight_key, request_id)


async def get_download_grant_detail(token: str) -> Optional[dict]:
    """
    获取下载授权详情
    
    Args:
        token: 下载token
        
    Returns:
        dict: 授权详情（None表示不存在或已失效）
    """
    token_hash = hash_token(token)
    grant_key = PREFIX_GRANT + token_hash
    
    grant_json = await redis_client.get(grant_key)
    if not grant_json:
        return None
    
    try:
        return json.loads(grant_json)
    except json.JSONDecodeError:
        return None


async def finish_download_grant(token: str):
    """
    标记下载完成
    
    Args:
        token: 下载token
    """
    token_hash = hash_token(token)
    grant_key = PREFIX_GRANT + token_hash
    
    grant_json = await redis_client.get(grant_key)
    if not grant_json:
        return
    
    try:
        grant_info = json.loads(grant_json)
        grant_info["status"] = "COMPLETED"
        
        # 设置较短的TTL（30秒）用于清理
        await redis_client.setex(grant_key, 30, json.dumps(grant_info))
        
        # 清理分块并发计数器
        inflight_key = PREFIX_CHUNK_INFLIGHT + token_hash
        await redis_client.delete(inflight_key)
    except json.JSONDecodeError:
        pass


async def fetch_file_metadata_from_business_service(file_id: str, user_id: str) -> dict:
    """
    从业务服务获取文件元数据
    
    Args:
        file_id: 文件ID
        user_id: 用户ID
        
    Returns:
        dict: 文件元数据
        
    Raises:
        HTTPException:
            - 404: 文件不存在或用户无权限
            - 503: 业务服务不可用
    """
    try:
        response = requests.get(
            f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/files/{file_id}?uid={user_id}",
            timeout=5
        )
        response.raise_for_status()
        result = response.json()
        
        if result.get("code") != 200:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="文件不存在用户网盘, 或者路径目录不存在"
            )
        
        return result.get("data", {})
    except requests.RequestException:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Business service unavailable"
        )
