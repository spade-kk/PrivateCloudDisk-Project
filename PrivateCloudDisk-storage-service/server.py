# FastAPI 文件上传示例
from fastapi import FastAPI, UploadFile, File, HTTPException, status, Header, Request, Depends, Query, Form
from fastapi.responses import JSONResponse, StreamingResponse, FileResponse, Response
from contextlib import asynccontextmanager
from typing import Optional, Literal
from pydantic import BaseModel, Field, EmailStr, constr, conint
from core.config import settings
import aiofiles
import hashlib
import os
import requests
import time
import jwt
import uuid
import redis
import json
import logging
import pyvips
import asyncio
import base64

# ---------- 服务端启动指令 -----------
# source .venv/bin/activate
# 历史兼容入口的原启动命令（保留注释供回溯）：uvicorn server:app --host 0.0.0.0 --port 8000 --reload
# [FIX-GIT-OBJECT-BROKER-20260817] 当前开发/生产统一使用 app.main:app；本 legacy 入口未注册
# /internal/v1/git/objects/{algorithm}/{object_hash}，不可作为 Git Service 的 Storage 进程入口。


app = FastAPI()

# ---------- 配置 ----------
REDIS_URL = settings.redis_url  # Redis 连接 URL
PRIVATE_KEY_PATH = settings.private_key_path   # 用于签发 ticket 的私钥路径
PUBLIC_KEY_PATH = settings.public_key_path  # 用于验证 ticket 的公钥路径
OPERATION_TOKEN_EXPIRE_SECONDS = settings.operation_token_expire_seconds                                    # 操作凭证有效期 10 分钟
MAX_CONCURRENT = settings.max_concurrent                                       # 单操作最大并发连接数
MAX_REQUESTS_PER_OPERATION_TOKEN = settings.max_requests_per_operation_token                                   # 单操作最大请求次数（可依据文件大小动态调整）
RATE_PER_SEC = settings.rate_per_sec                                                        # 单操作每秒最大请求数
MAX_RANGE_BYTES = settings.max_range_bytes                                      # 单次 Range 请求允许的最大字节数（这里设为 10 MB）
UPLOAD_DIR = settings.file_upload_dir                                    # 服务器存储上传文件的目录 /Uploads/storage 存放已经上传合并完成的文件 /Uploads 存放文件切片临时文件  
THUMBNAIL_TTL = settings.thumbnail_ttl
BUSINESS_SERVICE_URL = settings.business_service_url  # 业务服务地址
OPERATION_TOKEN_ISSUE_FILE_LIMIT = settings.operation_token_issue_file_limit
OPERATION_TOKEN_ISSUE_FILE_WINDOW_SECONDS = settings.operation_token_issue_file_window_seconds
OPERATION_TOKEN_DESTROY_USER_LIMIT = settings.operation_token_destroy_user_limit
OPERATION_TOKEN_DESTROY_USER_WINDOW_SECONDS = settings.operation_token_destroy_user_window_seconds
OPERATION_TOKEN_DESTROY_IP_LIMIT = settings.operation_token_destroy_ip_limit
OPERATION_TOKEN_DESTROY_IP_WINDOW_SECONDS = settings.operation_token_destroy_ip_window_seconds

# ---------- 加载密钥 ----------
with open(PRIVATE_KEY_PATH, "rb") as f:
    PRIVATE_KEY = f.read()
with open(PUBLIC_KEY_PATH, "rb") as f:
    PUBLIC_KEY = f.read()

# ---------- Redis 连接 ----------
redis_client = redis.asyncio.Redis.from_url(REDIS_URL, encoding="utf-8", decode_responses=True)

# ---------- Lua 脚本：原子并发控制 ----------
LUA_CONCURRENCY = """
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])
local current = redis.call('INCR', key)
redis.call('EXPIRE', key, ttl)
if current > limit then
    redis.call('DECR', key)
    return 0
end
return current
"""

LUA_RELEASE = """
local key = KEYS[1]
local current = redis.call('DECR', key)
if current <= 0 then
    redis.call('DEL', key)
    return 0
end
return current
"""

LUA_FIXED_WINDOW = """
local key = KEYS[1]
local ttl = tonumber(ARGV[1])
local current = redis.call('INCR', key)
if current == 1 then redis.call('EXPIRE', key, ttl) end
return current
"""

async def check_and_incr_concurrency(key: str, limit: int, ttl: int = 30) -> bool:
    """原子并发计数，超过 limit 返回 False, 否则 True"""
    current = await redis_client.eval(LUA_CONCURRENCY, 1, key, limit, ttl)
    return int(current) > 0

async def release_concurrency(key: str):
    await redis_client.eval(LUA_RELEASE, 1, key)

def get_client_ip(request: Request) -> str:
    forwarded_for = request.headers.get("x-forwarded-for")
    if forwarded_for:
        return forwarded_for.split(",")[0].strip()
    real_ip = request.headers.get("x-real-ip")
    if real_ip:
        return real_ip.strip()
    return request.client.host if request.client else "unknown"

def stable_hash(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()

async def enforce_fixed_window(key: str, limit: int, window_seconds: int, detail: str):
    if limit <= 0 or window_seconds <= 0:
        return
    current = await redis_client.eval(LUA_FIXED_WINDOW, 1, key, window_seconds)
    if int(current) > limit:
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail=detail)

async def enforce_operation_token_issue_limits(req: "InitOperationTokenRequest", user_id: str, client_ip: str):
    file_fingerprint = stable_hash(f"{req.file_id}|{req.operation_type}")
    # Gateway handles generic user/IP throttling. File service keeps file-operation semantics.
    await enforce_fixed_window(
        f"rl:operation_token:issue:file:{stable_hash(user_id)}:{file_fingerprint}",
        OPERATION_TOKEN_ISSUE_FILE_LIMIT,
        OPERATION_TOKEN_ISSUE_FILE_WINDOW_SECONDS,
        "Operation token requests are too frequent for this file"
    )

async def verify_operation_token(token: str) -> dict:
    """ 验证操作凭证 JWT,返回 payload, 失败抛出 401 """
    try:
        payload = jwt.decode(token, PUBLIC_KEY, algorithms=["RS256"],
                             options={"require": ["jti", "sub", "file_id", "operation_type", "exp", "rlimit"]})
        if await redis_client.exists(f"revoked:operation_token:{payload['jti']}"):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Operation token revoked")
        return payload
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Operation token expired")
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid operation token")
    
# ---------- 依赖：操作级多维限流 ----------
class OperationRateLimiter:
    def __init__(self, max_concurrent: int = MAX_CONCURRENT,
                 rate_per_sec: int = RATE_PER_SEC):
        self.max_concurrent = max_concurrent
        self.rate_per_sec = rate_per_sec

    async def __call__(self, request: Request,
                       token: str = Header(..., alias="X-Operation-Token"),
                       user_id: str = Header(..., alias="X-User-Id")):
        # 1. 验证 JWT，提取限制信息
        payload = await verify_operation_token(token)
        if payload["sub"] != user_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Operation token user mismatch")
        jti = payload["jti"]
        rlimit = payload["rlimit"]

        # 2. 总请求次数限制
        total_key = f"total:operation_token:{jti}"
        total = await redis_client.incr(total_key)
        if total == 1:
            await redis_client.expire(total_key, OPERATION_TOKEN_EXPIRE_SECONDS + 10)  # 稍长于 token 寿命
        if total > rlimit:
            raise HTTPException(status_code=429,
                                detail="The current number of operation requests has reached the upper limit")

        # 3. 每秒请求速率限制（固定窗口）
        rate_key = f"rate:operation_token:{jti}:{int(time.time())}"
        current_rate = await redis_client.incr(rate_key)
        if current_rate == 1:
            await redis_client.expire(rate_key, 2)  # 窗口保留 2 秒
        if current_rate > self.rate_per_sec:
            raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                                detail="Rate limit exceeded")

        # 4. 并发连接数限制
        concurrency_key = f"concurrency:operation_token:{jti}"
        allowed = await check_and_incr_concurrency(concurrency_key, self.max_concurrent)
        if not allowed:
            raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                                detail="Too many concurrent requests for this operation")

        # 存储并发 key 以便释放
        request.state.operation_token_concurrency_key = concurrency_key

        # 将 payload 存入 state 供路由使用（可选）
        request.state.operation_token_payload = payload

        try:
            yield
        finally:
            await release_concurrency(concurrency_key)

operation_limiter = OperationRateLimiter(max_concurrent=4)

async def get_thumbnail_bytes(file_path: str, width: int, height: int) -> tuple[bytes, str]:
    """
    使用 libvips 生成缩略图，Redis 缓存结果。
    返回 (图片字节, ETag)
    """
    # 1. 获取原图修改时间，作为版本标识
    try:
        mtime = os.path.getmtime(file_path)
    except OSError:
        mtime = 0

    # 2. 构建缓存键和 ETag
    etag = hashlib.md5(f"{file_path}{width}{height}{mtime}".encode()).hexdigest()
    cache_key = f"thumb:{etag}"

    # 3. 先查 Redis
    cached = await redis_client.get(cache_key)
    if cached is not None:
        img_bytes = base64.b64decode(cached)
        return img_bytes, etag

    # 4. 未命中，用 pyvips 生成（在线程池中执行，避免阻塞事件循环）
    def _generate():
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
                # strip=True,                  # 移除所有元数据（EXIF/ICC等），大幅减重
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

    img_base64 = base64.b64encode(img_bytes).decode('utf-8')
    # 5. 存入 Redis（字节流，设置过期时间）
    await redis_client.setex(cache_key, THUMBNAIL_TTL, img_base64)

    return img_bytes, etag

# ------------ 中间件 为每个接口计算处理请求业务时间
logging.basicConfig(level=logging.INFO)
middleware_logger = logging.getLogger("middleware")

@app.middleware("http")
async def add_process_time_header(request: Request, call_next):
    start = time.perf_counter()
    response = await call_next(request)
    elapsed = time.perf_counter() - start
    # 转换为毫秒
    elapsed_ms = elapsed * 1000
    response.headers["X-Process-Time"] = f"{elapsed_ms:.2f} ms"
    middleware_logger.info(f"{request.method} {request.url.path} - {elapsed_ms:.2f}ms")
    return response

class InitOperationTokenRequest(BaseModel):
    file_id: str
    operation_type: Literal["download", "preview", "stream"]

class OperationTokenCancelRequest(BaseModel):
    operation_token: str

# ---------- 接口：申请操作凭证 ----------
@app.post("/files/operation-tokens")
async def init_operation(
    req: InitOperationTokenRequest,
    request: Request,
    user_id:str = Header(..., alias="X-User-Id")
):  # 替换为实际认证
    await enforce_operation_token_issue_limits(req, user_id, get_client_ip(request))
    try:
        response = requests.get(f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/files/{req.file_id}?uid={user_id}", timeout=5)
        response.raise_for_status()
        result = response.json()
    except requests.RequestException:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Business service unavailable")

    if(result["code"] != 200):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="下载文件不存在用户网盘, 或者路径目录不存在"
        )

    # 此处可加入操作频率限制（每分钟最多申请几次），省略以保持简洁
    """签发操作凭证 JWT, 包含总请求次数上限"""
    now = int(time.time())
    payload = {
        "sub": user_id,
        "file_id": req.file_id,
        "operation_type": req.operation_type,
        "jti": str(uuid.uuid4()),
        "iat": now,
        "exp": now + OPERATION_TOKEN_EXPIRE_SECONDS,
        "rlimit": MAX_REQUESTS_PER_OPERATION_TOKEN,   # 总请求上限
    }
    token = jwt.encode(payload, PRIVATE_KEY, algorithm="RS256")

    sub = payload["sub"]
    jti = payload["jti"]
    file_sotrage_path = result["data"]["storage_path"]
    await redis_client.setex(
        f"operation_token_meta:{sub}:{jti}",
        OPERATION_TOKEN_EXPIRE_SECONDS + 30,
        json.dumps({
            "storage_path": file_sotrage_path,
            "file_size": result["data"]["size"],
            "file_name": result["data"]["name"]
        })
    )

    return JSONResponse({
        "code": 200,
        "data":
        {
            "operation_token": token
        },
        "message": None
    })

# ---------- 接口：销毁操作凭证 ----------
@app.delete("/files/operation-tokens")
@app.delete("/files/operation-tokens/")
async def destroy_operation_token(
    request: Request,
    req: Optional[OperationTokenCancelRequest] = None,
    operation_token: Optional[str] = Query(None),
    user_id: str = Header(..., alias="X-User-Id")
):
    client_ip = get_client_ip(request)
    await enforce_fixed_window(
        f"rl:operation_token:destroy:user:{stable_hash(user_id)}",
        OPERATION_TOKEN_DESTROY_USER_LIMIT,
        OPERATION_TOKEN_DESTROY_USER_WINDOW_SECONDS,
        "Operation token destroy requests are too frequent for this user"
    )
    await enforce_fixed_window(
        f"rl:operation_token:destroy:ip:{stable_hash(client_ip)}",
        OPERATION_TOKEN_DESTROY_IP_LIMIT,
        OPERATION_TOKEN_DESTROY_IP_WINDOW_SECONDS,
        "Operation token destroy requests are too frequent from this client"
    )

    token = operation_token or (req.operation_token if req else None)
    if not token:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="operation_token is required")

    payload = await verify_operation_token(token)
    if payload["sub"] != user_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Operation token user mismatch")

    now = int(time.time())
    ttl = max(1, int(payload["exp"]) - now + 30)
    jti = payload["jti"]
    sub = payload["sub"]
    await redis_client.setex(f"revoked:operation_token:{jti}", ttl, sub)
    await redis_client.delete(
        f"operation_token_meta:{sub}:{jti}",
        f"total:operation_token:{jti}",
        f"concurrency:operation_token:{jti}"
    )
    return JSONResponse({"code": 200, "data": None, "message": None})

# ---------- 接口：下载文件（支持Range） ----------
@app.get("/files/files/{file_id}/content")
async def download_file(
    file_id: str,
    request: Request,
    user_id: str = Header(..., alias="X-User-Id"),
    range_header: Optional[str] = Header(None, alias="Range"),
    _: None = Depends(operation_limiter)
):  
    # 从 state 中拿到之前验证好的 payload
    payload = request.state.operation_token_payload
    jti = payload["jti"]
    sub = payload["sub"]
    if sub != user_id:
        raise HTTPException(status_code=403, detail="Operation token user mismatch")
    # 交叉校验请求路径与凭证中的 file_id
    if payload["file_id"] != file_id:
        raise HTTPException(status_code=403, detail="Operation Token not for this file")
    
    # 从缓存获取文件元数据（Redis 中存储，签发时已写入）
    data = await redis_client.get(f"operation_token_meta:{sub}:{jti}")
    if data:
        metadata = json.loads(data)
    else:
        # 降级查库（极少数情况）
        response = requests.get(f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/files/{file_id}?uid={user_id}")
        result = response.json()
        if result["code"] != 200:
            raise HTTPException(status_code=404, detail="文件不存在用户网盘, 或者路径目录不存在")
        
        metadata = {
            "storage_path": result["data"]["storage_path"],
            "file_size": result["data"]["size"],
            "file_name": result["data"]["name"]
        }
        
        await redis_client.setex(
            f"operation_token_meta:{sub}:{jti}",
            OPERATION_TOKEN_EXPIRE_SECONDS,
            json.dumps(metadata)
        )

    file_storage_path = metadata["storage_path"]
    file_size = metadata["file_size"]
    file_name = metadata["file_name"]
    start, end = 0, file_size - 1

    if range_header:
        unit, _, ranges = range_header.partition("=")
        if unit.strip() == "bytes":
            ranges = ranges.strip()
            start_str, _, end_str = ranges.partition("-")
            try:
                start = int(start_str) if start_str else 0
                end = int(end_str) if end_str else file_size - 1
            except ValueError:
                logging.info(ranges)
                raise HTTPException(status_code=400, detail="Invalid Range header")
            if start >= file_size or end >= file_size or start > end:
                raise HTTPException(status_code=416, detail="Range not satisfiable")

        content_length = end - start + 1
        if(content_length > MAX_RANGE_BYTES):
            raise HTTPException(status_code=status.HTTP_416_REQUESTED_RANGE_NOT_SATISFIABLE, 
                                detail=f"Requested range exceeds maximum allowed size of {MAX_RANGE_BYTES} bytes",
                                headers={
                                    "Content-Range": f"bytes */{file_size}",
                                    "X-Max-Range-Size": str(MAX_RANGE_BYTES)
                                }
                            )
        
        # 流式返回文件片段
        async def file_iterator():
            async with aiofiles.open(file_storage_path, "rb") as f:
                await f.seek(start)
                remaining = content_length
                while remaining > 0:
                    chunk_size = min(8192, remaining)
                    data = await f.read(chunk_size)
                    if not data:
                        break
                    remaining -= len(data)
                    yield data

        headers = {
            "Content-Range": f"bytes {start}-{end}/{file_size}",
            "Content-Length": str(content_length),
            "Accept-Ranges": "bytes",
        }

        return StreamingResponse(
            file_iterator(),
            status_code=206,
            headers=headers,
            media_type="application/octet-stream"
        )
    else: # 同样需要限制无range全文件下载的时候的最大大小
        content_length = file_size
        if content_length > MAX_RANGE_BYTES:
            raise HTTPException(
                status_code=400,  # 或 416，无 Range 时用 400 更合适
                detail=f"File too large to download without Range header. Max allowed size: {MAX_RANGE_BYTES} bytes",
                headers={"X-Max-File-Size": str(MAX_RANGE_BYTES)}
            )
        # 文件大小在允许范围内，返回完整内容，状态码 200
        # 使用 FileResponse 更高效（自动处理 Accept-Ranges、Content-Length 等）
        return FileResponse(
            path=file_storage_path,
            filename=file_name,
            media_type="application/octet-stream",
            headers={"Accept-Ranges": "bytes"}  # 告知客户端支持 Range
        )

@app.post("/files/uploads/{uploads_id}/chunks")
@app.post("/files/uploads/{uploads_id}/chunks/")
async def upload_chunk(
    uploads_id: str,
    chunk_index: int = Form(...),
    file: UploadFile = File(...),
    user_id:str = Header(..., alias="X-User-Id")
):
    chunk_path = f"{UPLOAD_DIR}/{uploads_id}-{chunk_index}.part"

    response = requests.get(f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/uploads/{uploads_id}")
    result = response.json()
    response = requests.get(f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/uploads/{uploads_id}/chunks/{chunk_index}")
    chunk_result = response.json()

    if(result["code"] == 15000):
        raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"会话不存在"
            )
    if(result["data"]["user_id"] != user_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="上传操作令牌持有用户与当前用户不匹配"
        )
    if(chunk_result["data"] != None):
        raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"此索引切片已经上传过了"
            )
    if(file.size > result["data"]["chunks_max_size"]):
        raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"切片大小超过限制"
            )
    if(chunk_index <= 0 or chunk_index > result["data"]["total_chunks"]):
        raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"切片索引不在总范围内"
            )
    # if(time.time() > result["endding_time"]):
    #     raise HTTPException(
    #             status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
    #             detail=f"会话已失效"
    #         )
    # if(file.content_type != result["file_type"]):
    #     raise HTTPException(
    #             status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
    #             detail=f"切片文件类型: {file.content_type}与原文件不一致"
    #         )
    # 异步流式写入
    async with aiofiles.open(chunk_path, 'wb') as f:
        while chunk := await file.read(64 * 1024):  # 64KB块
            await f.write(chunk)
    
    response = requests.post(
        f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/uploads/{uploads_id}/chunks/{chunk_index}/complete",
        params = {
            "storage_path":chunk_path
            }
        )

    return JSONResponse(
        {
            "code":200, 
            "data":
            {
                "chunk": chunk_index
            }, 
            "message": None
        })

@app.post("/files/uploads/{uploads_id}/merge")
async def complete_uploads_internal(
    uploads_id: str,
    user_id:str = Header(..., alias="X-User-Id")
):
    response = requests.get(f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/uploads/{uploads_id}")
    result = response.json()

    if(result["code"] == 15000):
        raise HTTPException(
                status_code = status.HTTP_503_SERVICE_UNAVAILABLE,
                detail = f"会话不存在"
            )
    if(result["data"]["user_id"] != user_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="上传操作令牌持有用户与当前用户不匹配"
        )
    if(result["data"]["status"] != "uploading"):
        raise HTTPException(
                    status_code = status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail = f"会话状态错误"
                )
    """ 开始处理合并请求逻辑 提交上传会话状态 合并中... 逻辑锁防止并发异常多次处理 """
    response = requests.post(f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/uploads/{uploads_id}/merge")
    merging_result = response.json()

    if(merging_result["code"] != 200):
        raise HTTPException(
                status_code = status.HTTP_503_SERVICE_UNAVAILABLE,
                detail = f"合并状态申请失败"
            )

    """合并文件并验证完整性"""
    session_dir = f"{UPLOAD_DIR}/{uploads_id}"
    total_chunks = result["data"]["total_chunks"]
    final_path = f"{UPLOAD_DIR}/storage/{uploads_id}-{total_chunks}.cloud"
    file_hash = hashlib.sha256()

    # 创建最终文件
    async with aiofiles.open(final_path, "wb") as final_file:
    # 按顺序合并分片
        for i in range(1, result["data"]["total_chunks"] + 1):
            chunk_path = f"{session_dir}-{i}.part"
            # 流式读取分片并计算完整哈希
            async with aiofiles.open(chunk_path, "rb") as chunk_file:
                while content := await chunk_file.read(128 * 1024):  # 128KB块
                    await final_file.write(content)
                    file_hash.update(content)
                # 删除分片
                os.remove(chunk_path)

    # 验证完整文件校验码
    actual_checksum = file_hash.hexdigest()

    response = requests.post(
    f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/files",
    params = {
        "uploads_id": uploads_id,
        "file_storage_path": final_path
        }
    )

    return JSONResponse(
        {
            "code":200, 
            "data":
            {
                "status": actual_checksum
            }, 
            "message": None
        })

@app.get("/files/nodes/{node_id}/thumbnails/{file_name}")
async def get_thumbnail(
    node_id: str,
    file_name: str,
    request: Request,
    user_id: str = Header(..., alias="X-User-Id"),
    width: int = Query(200, ge=50, le=800),
    height: int = Query(200, ge=50, le=800)
):
    response = requests.get(f"{BUSINESS_SERVICE_URL}/api/v1/business/internal/storage/files/{node_id}/{file_name}?uid={user_id}")
    result = response.json()

    if(result["code"] != 200):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="文件不存在用户网盘, 或者路径目录不存在"
        )

    # 3. 获取缩略图数据
    img_bytes, etag = await get_thumbnail_bytes(result["data"]["storage_path"], width, height)

    # 4. 检查浏览器缓存
    if request.headers.get("If-None-Match") == etag:
        return Response(status_code=304)

    # 5. 返回图片流
    return Response(
        content=img_bytes,
        media_type="image/jpeg",
        headers={
            "Cache-Control": f"public, max-age={THUMBNAIL_TTL}",
            "ETag": etag
        }
    )

@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时
    yield
    # 关闭时
    await redis_client.close()
