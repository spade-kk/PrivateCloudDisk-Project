"""
安全模块
提供 JWT 操作凭证验证和密钥管理
"""
import jwt
from fastapi import HTTPException, status
from core.config import settings
from app.core.redis_client import redis_client


# 加载 RSA 密钥对
PRIVATE_KEY_PATH = settings.private_key_path
PUBLIC_KEY_PATH = settings.public_key_path

with open(PRIVATE_KEY_PATH, "rb") as f:
    PRIVATE_KEY = f.read()

with open(PUBLIC_KEY_PATH, "rb") as f:
    PUBLIC_KEY = f.read()

# 操作凭证有效期（秒）
OPERATION_TOKEN_EXPIRE_SECONDS = settings.operation_token_expire_seconds


async def verify_operation_token(token: str) -> dict:
    """
    验证操作凭证 JWT
    
    验证流程：
    1. 解码 JWT，验证签名和必需字段
    2. 检查令牌是否已被撤销
    3. 返回令牌载荷
    
    JWT 载荷必需字段：
    - jti: 令牌唯一标识符
    - sub: 用户 ID
    - file_id: 文件 ID
    - operation_type: 操作类型
    - exp: 过期时间
    - rlimit: 请求次数限制
    
    Args:
        token: JWT 操作凭证字符串
    
    Returns:
        dict: JWT 载荷字典，包含用户信息和限制信息
    
    Raises:
        HTTPException: 
            - 401: 令牌无效、已过期或已撤销
            - 403: 用户不匹配
    
    Example:
        >>> payload = await verify_operation_token(token)
        >>> print(payload["sub"])  # 用户 ID
        >>> print(payload["file_id"])  # 文件 ID
    """
    try:
        payload = jwt.decode(
            token, 
            PUBLIC_KEY, 
            algorithms=["RS256"],
            options={"require": ["jti", "sub", "file_id", "operation_type", "exp", "rlimit"]}
        )
        if await redis_client.exists(f"revoked:operation_token:{payload['jti']}"):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Operation token revoked")
        return payload
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Operation token expired")
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid operation token")
