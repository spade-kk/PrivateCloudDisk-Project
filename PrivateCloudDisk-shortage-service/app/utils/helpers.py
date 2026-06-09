"""
辅助工具函数
提供通用的工具函数
"""
import hashlib
from fastapi import Request


def get_client_ip(request: Request) -> str:
    """
    获取客户端真实 IP 地址
    
    优先从代理头中获取真实 IP，支持多种代理场景：
    1. X-Forwarded-For: 标准代理头，取第一个 IP
    2. X-Real-IP: Nginx 常用的真实 IP 头
    3. request.client.host: 直接连接的客户端 IP
    
    Args:
        request: FastAPI 请求对象
    
    Returns:
        str: 客户端 IP 地址字符串
    
    Example:
        >>> ip = get_client_ip(request)
        >>> print(ip)  # "192.168.1.100"
    """
    forwarded_for = request.headers.get("x-forwarded-for")
    if forwarded_for:
        return forwarded_for.split(",")[0].strip()
    real_ip = request.headers.get("x-real-ip")
    if real_ip:
        return real_ip.strip()
    return request.client.host if request.client else "unknown"


def stable_hash(value: str) -> str:
    """
    生成稳定的 SHA256 哈希值
    
    使用 SHA256 算法生成字符串的哈希值，用于：
    1. 生成限流键的唯一标识
    2. 避免在 Redis 键中直接存储敏感信息
    
    Args:
        value: 需要哈希的字符串
    
    Returns:
        str: 64位十六进制哈希字符串
    
    Example:
        >>> hash_value = stable_hash("user123|download")
        >>> print(hash_value)  # "a1b2c3d4..."
    """
    return hashlib.sha256(value.encode("utf-8")).hexdigest()
