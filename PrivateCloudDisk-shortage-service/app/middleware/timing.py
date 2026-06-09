"""
请求时间中间件
记录每个请求的处理时间
"""
import time
import logging
from fastapi import Request


# 配置日志
logging.basicConfig(level=logging.INFO)
middleware_logger = logging.getLogger("middleware")


async def add_process_time_header(request: Request, call_next):
    """
    添加请求处理时间头的中间件
    
    功能：
    1. 记录请求开始时间
    2. 执行请求处理
    3. 计算处理耗时
    4. 在响应头中添加 X-Process-Time 头
    5. 记录日志
    
    响应头格式：
        X-Process-Time: 123.45 ms
    
    Args:
        request: FastAPI 请求对象
        call_next: 下一个中间件或路由处理函数
    
    Returns:
        Response: 添加了处理时间头的响应对象
    
    Example:
        >>> # 在 main.py 中注册
        >>> app.middleware("http")(add_process_time_header)
    """
    start = time.perf_counter()
    response = await call_next(request)
    elapsed = time.perf_counter() - start
    elapsed_ms = elapsed * 1000
    response.headers["X-Process-Time"] = f"{elapsed_ms:.2f} ms"
    middleware_logger.info(f"{request.method} {request.url.path} - {elapsed_ms:.2f}ms")
    return response
