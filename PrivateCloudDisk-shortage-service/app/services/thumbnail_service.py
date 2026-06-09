"""
缩略图生成服务
使用 libvips 生成高质量缩略图，支持 Redis 缓存
"""
import os
import hashlib
import base64
import asyncio
import logging
import pyvips
from fastapi import HTTPException
from core.config import settings
from core.redis_client import redis_client


# 缩略图缓存 TTL（秒）
THUMBNAIL_TTL = settings.thumbnail_ttl

# 日志记录器
logger = logging.getLogger(__name__)


async def get_thumbnail_bytes(file_path: str, width: int, height: int) -> tuple[bytes, str]:
    """
    使用 libvips 生成缩略图，支持 Redis 缓存
    
    功能特点：
    1. 使用 libvips 高性能图片处理库
    2. 等比缩放，不超过目标宽高
    3. Lanczos3 重采样算法，高质量缩放
    4. JPEG 编码优化（质量85，优化编码，Trellis量化）
    5. Redis 缓存，基于文件路径+尺寸+修改时间的 ETag
    
    处理流程：
    1. 获取文件修改时间，构建缓存键和 ETag
    2. 查询 Redis 缓存，命中则直接返回
    3. 未命中，使用 libvips 生成缩略图
    4. 将缩略图存入 Redis 缓存
    5. 返回缩略图字节和 ETag
    
    Args:
        file_path: 原图文件路径
        width: 目标宽度（像素）
        height: 目标高度（像素）
    
    Returns:
        tuple[bytes, str]: (缩略图字节数据, ETag字符串)
    
    Raises:
        HTTPException: 
            - 500: libvips 处理失败或其他异常
    
    Example:
        >>> img_bytes, etag = await get_thumbnail_bytes("/path/to/image.jpg", 200, 200)
        >>> # 返回缩略图和 ETag
    """
    # 1. 获取原图修改时间，作为版本标识
    try:
        mtime = os.path.getmtime(file_path)
    except OSError:
        mtime = 0

    # 2. 构建缓存键和 ETag
    etag = hashlib.md5(f"{file_path}{width}{height}{mtime}".encode()).hexdigest()
    cache_key = f"thumb:{etag}"

    # 3. 先查 Redis 缓存
    cached = await redis_client.get(cache_key)
    if cached is not None:
        img_bytes = base64.b64decode(cached)
        return img_bytes, etag

    # 4. 未命中，用 pyvips 生成（在线程池中执行，避免阻塞事件循环）
    def _generate():
        """
        内部函数：使用 pyvips 生成缩略图
        
        处理步骤：
        1. 加载图像（顺序访问模式，节省内存）
        2. 计算等比缩放比例
        3. Lanczos3 重采样缩放
        4. 色彩空间转换为 sRGB
        5. JPEG 编码保存到缓冲区
        """
        try:
            # 打开图像，vips 自动识别格式
            image = pyvips.Image.new_from_file(file_path, access='sequential')
            # 计算缩放比例（等比缩放，不超过目标宽高）
            scale = min(width / image.width, height / image.height)
            if scale < 1.0:
                image = image.resize(scale, kernel='lanczos3')
            # 统一色彩空间
            if image.interpretation != pyvips.Interpretation.SRGB:
                image = image.colourspace(pyvips.Interpretation.SRGB)
            # JPEG 编码优化（关键！）
            out_buffer = image.jpegsave_buffer(
                Q=85,                        # 质量 85（可调 80-90）
                optimize_coding=True,        # 优化 Huffman 表，减小体积
                trellis_quant=True,          # Trellis 量化，提升压缩效率
                overshoot_deringing=True,    # 去振铃，提升视觉质量
                interlace=False              # 非渐进式 JPEG，体积更小
            )
            return out_buffer
        except Exception as e:
            raise RuntimeError(f"libvips 处理失败: {str(e)}")

    try:
        # asyncio.to_thread 是 Python 3.9+ 推荐方式，等同于 run_in_executor
        img_bytes = await asyncio.to_thread(_generate)
    except RuntimeError as e:
        raise HTTPException(status_code=500, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"缩略图生成异常: {str(e)}")

    # 5. 存入 Redis 缓存（字节流，设置过期时间）
    img_base64 = base64.b64encode(img_bytes).decode('utf-8')
    await redis_client.setex(cache_key, THUMBNAIL_TTL, img_base64)

    return img_bytes, etag
