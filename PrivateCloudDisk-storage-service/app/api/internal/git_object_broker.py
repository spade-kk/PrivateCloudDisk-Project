"""Git Service 专用的内容寻址 Object Broker。

Git Service 与普通文件服务共享 StorageProvider 的物理后端，
但只能访问 ``git/objects`` 命名空间。接口不挂载到 Gateway；服务令牌、Hash 形状、
解压后的 Git canonical hash、传输 SHA-256、大小和 Range 均在此边界校验。
"""
from __future__ import annotations

import hashlib
import hmac
import os
import re
import tempfile
import zlib
from pathlib import Path

from fastapi import APIRouter, Header, HTTPException, Request, Response, status
from fastapi.responses import StreamingResponse

from app.core.file_delivery import parse_single_range
from core.config import settings
from core.storage.factory import get_storage

router = APIRouter(prefix="/internal/v1/git/objects", tags=["内部 Git Object Broker"])
_HASH_LENGTHS = {"sha1": 40, "sha256": 64}
_HEX = re.compile(r"^[0-9a-f]+$")


def _verify_service_token(value: str | None) -> None:
    expected = settings.pcd_internal_service_token
    if not expected:
        raise HTTPException(status_code=503, detail="Git Object Broker 尚未配置内部服务凭证")
    if not value or not hmac.compare_digest(value, expected):
        raise HTTPException(status_code=401, detail="内部服务认证失败")


def _object_path(algorithm: str, object_hash: str) -> str:
    algorithm = algorithm.lower()
    object_hash = object_hash.lower()
    if algorithm not in _HASH_LENGTHS or len(object_hash) != _HASH_LENGTHS[algorithm] or not _HEX.fullmatch(object_hash):
        raise HTTPException(status_code=422, detail="Git Object Hash 格式无效")
    # 物理布局保持 Git 原生 objects/xx/xxxx，并以算法命名空间隔离 SHA-1/SHA-256。
    return f"git/objects/{algorithm}/{object_hash[:2]}/{object_hash[2:]}"


@router.put("/{algorithm}/{object_hash}", include_in_schema=False)
async def put_object(
    algorithm: str,
    object_hash: str,
    request: Request,
    x_pcd_service_token: str | None = Header(default=None, alias="X-PCD-Service-Token"),
    x_content_sha256: str | None = Header(default=None, alias="X-Content-SHA256"),
):
    _verify_service_token(x_pcd_service_token)
    storage_path = _object_path(algorithm, object_hash)
    if not x_content_sha256 or not re.fullmatch(r"[0-9a-fA-F]{64}", x_content_sha256):
        raise HTTPException(status_code=422, detail="缺少有效的传输 SHA-256")

    temporary_dir = Path(settings.file_upload_dir).resolve() / ".git-object-broker"
    temporary_dir.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix="object-", suffix=".tmp", dir=temporary_dir)
    transport_digest = hashlib.sha256()
    canonical_digest = hashlib.new(algorithm)
    decompressor = zlib.decompressobj()
    compressed_size = 0
    canonical_size = 0
    try:
        with os.fdopen(descriptor, "wb") as output:
            async for chunk in request.stream():
                if not chunk:
                    continue
                compressed_size += len(chunk)
                if compressed_size > settings.git_object_max_bytes:
                    raise HTTPException(status_code=413, detail="Git Object 超过允许大小")
                output.write(chunk)
                transport_digest.update(chunk)
                try:
                    canonical = decompressor.decompress(chunk)
                except zlib.error as exc:
                    raise HTTPException(status_code=422, detail="Git Object 不是有效的 zlib 对象") from exc
                canonical_size += len(canonical)
                if canonical_size > settings.git_object_max_bytes:
                    raise HTTPException(status_code=413, detail="Git Object 解压后超过允许大小")
                canonical_digest.update(canonical)
            tail = decompressor.flush()
            canonical_size += len(tail)
            canonical_digest.update(tail)
            output.flush()
            os.fsync(output.fileno())

        if not decompressor.eof or decompressor.unused_data or canonical_size <= 0:
            raise HTTPException(status_code=422, detail="Git Object 压缩流不完整")
        if not hmac.compare_digest(transport_digest.hexdigest(), x_content_sha256.lower()):
            raise HTTPException(status_code=409, detail="Git Object 传输校验失败")
        if not hmac.compare_digest(canonical_digest.hexdigest(), object_hash.lower()):
            raise HTTPException(status_code=409, detail="Git canonical hash 与对象地址不一致")

        storage = get_storage()
        existed = await storage.exists(storage_path)
        if not existed:
            await storage.put_file(storage_path, temporary_name)
        return {"object_hash": object_hash.lower(), "size": compressed_size, "deduplicated": existed}
    finally:
        Path(temporary_name).unlink(missing_ok=True)


@router.head("/{algorithm}/{object_hash}", include_in_schema=False)
async def head_object(
    algorithm: str,
    object_hash: str,
    x_pcd_service_token: str | None = Header(default=None, alias="X-PCD-Service-Token"),
):
    _verify_service_token(x_pcd_service_token)
    storage = get_storage()
    storage_path = _object_path(algorithm, object_hash)
    if not await storage.exists(storage_path):
        raise HTTPException(status_code=404, detail="Git Object 不存在")
    metadata = await storage.stat(storage_path)
    return Response(status_code=200, headers={"Content-Length": str(metadata["size"]), "ETag": object_hash.lower()})


@router.get("/{algorithm}/{object_hash}", include_in_schema=False)
async def get_object(
    algorithm: str,
    object_hash: str,
    range_value: str | None = Header(default=None, alias="Range"),
    x_pcd_service_token: str | None = Header(default=None, alias="X-PCD-Service-Token"),
):
    _verify_service_token(x_pcd_service_token)
    storage = get_storage()
    storage_path = _object_path(algorithm, object_hash)
    if not await storage.exists(storage_path):
        raise HTTPException(status_code=404, detail="Git Object 不存在")
    metadata = await storage.stat(storage_path)
    size = int(metadata["size"])
    parsed = parse_single_range(range_value, size, settings.git_object_max_range_bytes)
    start, end = parsed if parsed else (0, size - 1)
    length = max(0, end - start + 1)

    async def iterator():
        offset = start
        remaining = length
        while remaining > 0:
            read_size = min(1024 * 1024, remaining)
            chunk = await storage.get(storage_path, offset=offset, length=read_size)
            if not chunk:
                break
            offset += len(chunk)
            remaining -= len(chunk)
            yield chunk

    headers = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(length),
        "Cache-Control": "private, immutable, max-age=31536000",
        "ETag": object_hash.lower(),
        "X-Content-Type-Options": "nosniff",
    }
    response_status = 200
    if parsed:
        response_status = 206
        headers["Content-Range"] = f"bytes {start}-{end}/{size}"
    return StreamingResponse(iterator(), status_code=response_status, media_type="application/octet-stream", headers=headers)


@router.delete("/{algorithm}/{object_hash}", include_in_schema=False)
async def delete_object(
    algorithm: str,
    object_hash: str,
    x_pcd_service_token: str | None = Header(default=None, alias="X-PCD-Service-Token"),
):
    _verify_service_token(x_pcd_service_token)
    await get_storage().delete(_object_path(algorithm, object_hash))
    return Response(status_code=status.HTTP_204_NO_CONTENT)
