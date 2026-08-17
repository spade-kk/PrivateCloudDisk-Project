"""
企业级业务服务客户端 SDK (Business Service Client)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
统一封装与主业务服务 (Business Service) 的所有 HTTP 通信。

设计目标：
  1. 统一异步请求库：httpx — FastAPI 生态官方推荐，纯异步，完美契合 Starlette 事件循环
  2. 消除同步阻塞：替换项目中散落的 requests（同步阻塞，会导致 FastAPI 事件循环饥饿）
  3. 消除库碎片化：统一替换 aiohttp / requests / httpx 三足鼎立 → 仅 httpx
  4. 连接池复用：单进程内复用 AsyncClient，减少 TCP 三次握手开销
  5. 超时控制：连接超时 + 读取超时 + 写入超时，防止下游雪崩
  6. 结构化日志：记录每次调用的请求/响应关键信息，便于链路追踪
  7. 类型安全：所有方法返回值带完整类型注解

与 core/services/notification_service.py 的区别：
  - notification_service.py 是全局项目 (core/) 的 Worker 消费者通知服务
  - 本 SDK 是 app/core/ 内 FastAPI Web 层的业务服务客户端
  - 本 SDK 覆盖更广的 API 面（文件 CRUD + 上传会话管理 + 通知回调）
  - 本 SDK 按事件循环缓存 AsyncClient，避免跨事件循环问题

使用方式：
  from app.core.business_service_client import business_service_client

  # 获取文件元数据
  metadata = await business_service_client.get_file_metadata(file_id, user_id)

  # 通知文件合并完成
  await business_service_client.notify_file_merged(uploads_id, storage_path, file_id, user_id)

  # 获取上传会话
  session = await business_service_client.get_upload_session(uploads_id)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"""
from __future__ import annotations

import asyncio
import logging
from typing import Any, Optional

import httpx
from core.config import settings

logger = logging.getLogger("business_service_client")

# ============================
# HTTP 客户端配置
# ============================

# 超时配置：连接 5s，读取 30s，写入 30s，总超时 60s
_CLIENT_TIMEOUT = httpx.Timeout(
    connect=5.0,   # TCP 连接超时
    read=30.0,     # 读取响应超时
    write=30.0,    # 写入请求超时
    pool=5.0,      # 连接池获取超时
)

# 连接池限制：最多 20 个活跃连接，每个主机最多 10 个保持活跃
_CLIENT_LIMITS = httpx.Limits(
    max_connections=20,
    max_keepalive_connections=10,
    keepalive_expiry=30.0,  # 空闲连接 30 秒后回收
)

# ============================
# 业务服务 API 路径常量
# ============================

class BusinessServicePaths:
    """业务服务内部 API 路径"""
    # Sprint 0：原路径包含网关前缀 /api/v1；新路径用于容器私网直连业务服务。
    # 文件相关
    FILE_METADATA = "/business/internal/storage/files/{file_id}"        # GET 获取文件元数据
    FILE_BY_NODE = "/business/internal/storage/files/{node_id}/{file_name}"  # GET 按节点+文件名获取
    FILE_MERGED = "/business/internal/storage/files"                    # POST 通知文件合并完成
    FILE_ACTIVATE = "/business/internal/storage/files/{file_id}/activate"       # POST 标记文件活跃
    FILE_STATUS = "/business/internal/storage/files/{file_id}/status"           # PATCH 更新文件状态
    FILE_DELETE_COMPLETE = "/business/internal/storage/files/{file_id}/delete-complete"  # POST 删除完成回调
    FILE_HLS_READY = "/business/internal/storage/files/{file_id}/hls-ready"     # POST HLS 转码完成

    # 需求一/二：分享资源授权必须由主业务服务验证 share token、虚拟资源 ID
    # 与资源归属；文件服务只接收脱敏后的内部元数据，不直接查询分享库。
    SHARE_RESOURCE_ACCESS = "/business/internal/storage/shares/{share_token}/resources/{share_resource_id}"

    # 上传会话相关
    UPLOAD_SESSION = "/business/internal/storage/uploads/{uploads_id}"                  # GET 获取上传会话
    UPLOAD_CHUNK_STATUS = "/business/internal/storage/uploads/{uploads_id}/chunks/{chunk_index}"  # GET 按分片状态
    UPLOAD_CHUNK_COMPLETE = "/business/internal/storage/uploads/{uploads_id}/chunks/{chunk_index}/complete"  # POST 分片完成
    UPLOAD_MERGE = "/business/internal/storage/uploads/{uploads_id}/merge"              # POST 合并分片
    UPLOAD_MERGE_CLEANUP = "/business/internal/storage/uploads/{uploads_id}/merge-cleanup"  # POST 合并后清理会话
    UPLOAD_DELETE_COMPLETE = "/business/internal/storage/uploads/{uploads_id}/delete-complete"  # POST 删除会话完成


# ============================
# 客户端实现
# ============================

class BusinessServiceClient:
    """
    业务服务客户端 SDK

    封装所有与主业务服务 (Business Service) 的 HTTP 通信。
    使用 httpx.AsyncClient 实现纯异步非阻塞调用。

    设计要点：
      - 按事件循环缓存 AsyncClient，避免跨事件循环异常
      - 每个事件循环内复用连接池，减少 TCP 握手开销
      - 所有方法均为 async，完美契合 FastAPI 异步模型
    """

    # 按事件循环 ID 缓存 AsyncClient 实例
    _clients: dict[int, httpx.AsyncClient] = {}

    def __init__(self):
        """初始化客户端（不创建 AsyncClient，懒加载）"""
        self._base_url = settings.business_service_url.rstrip("/")

    async def _get_client(self) -> httpx.AsyncClient:
        """
        获取当前事件循环对应的 AsyncClient（懒加载 + 事件循环级缓存）

        为什么按事件循环缓存而不是全局单例？
          - FastAPI Web 进程：一个主事件循环，一个 AsyncClient 即可
          - Worker 子进程：asyncio.run() 会创建新的事件循环
          - 如果跨事件循环共享 AsyncClient，会抛出 "Event loop is closed" 异常
          - 按事件循环缓存可以安全复用，同时避免跨循环问题
        """
        try:
            loop = asyncio.get_running_loop()
        except RuntimeError:
            # 当前没有运行中的事件循环（同步代码中调用），创建新的
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)

        loop_id = id(loop)

        if loop_id not in self._clients or self._clients[loop_id].is_closed:
            default_headers = {}
            if settings.pcd_internal_service_token:
                default_headers["X-PCD-Service-Token"] = settings.pcd_internal_service_token
            self._clients[loop_id] = httpx.AsyncClient(
                base_url=self._base_url,
                timeout=_CLIENT_TIMEOUT,
                limits=_CLIENT_LIMITS,
                trust_env=False,  # 不信任系统代理环境变量
                headers=default_headers,
            )
            logger.debug("BusinessServiceClient: 创建新 AsyncClient (loop_id=%s)", loop_id)

        return self._clients[loop_id]

    async def close(self):
        """关闭所有事件循环的 AsyncClient（应用关闭时调用）"""
        for loop_id, client in list(self._clients.items()):
            if not client.is_closed:
                await client.aclose()
                logger.debug("BusinessServiceClient: 关闭 AsyncClient (loop_id=%s)", loop_id)
        self._clients.clear()

    # ============================
    # 内部辅助方法
    # ============================

    async def _get(
        self,
        path: str,
        params: dict[str, Any] | None = None,
        headers: dict[str, str] | None = None,
    ) -> dict[str, Any]:
        """
        内部 GET 请求封装

        Args:
            path: API 路径（相对 base_url）
            params: URL 查询参数

        Returns:
            解析后的 JSON 响应体

        Raises:
            BusinessServiceError: 业务服务不可用或返回错误
        """
        client = await self._get_client()
        logger.debug("BusinessServiceClient GET %s params=%s", path, params)
        try:
            resp = await client.get(path, params=params, headers=headers)
            resp.raise_for_status()
            return resp.json()
        except httpx.TimeoutException:
            logger.error("BusinessServiceClient GET %s 超时", path)
            raise BusinessServiceError("业务服务请求超时", status_code=504)
        except httpx.HTTPStatusError as e:
            logger.error("BusinessServiceClient GET %s HTTP %s: %s", path, e.response.status_code, e.response.text[:200])
            raise BusinessServiceError(
                f"业务服务返回错误: HTTP {e.response.status_code}",
                status_code=e.response.status_code,
            )
        except httpx.RequestError as e:
            logger.error("BusinessServiceClient GET %s 网络异常: %s", path, e)
            raise BusinessServiceError("无法连接业务服务", status_code=502)

    async def _post(
        self,
        path: str,
        params: dict[str, Any] | None = None,
        json_data: dict[str, Any] | None = None,
        headers: dict[str, str] | None = None,
    ) -> dict[str, Any]:
        """
        内部 POST 请求封装

        Args:
            path: API 路径
            params: URL 查询参数
            json_data: 请求体 JSON

        Returns:
            解析后的 JSON 响应体
        """
        client = await self._get_client()
        logger.debug("BusinessServiceClient POST %s params=%s json=%s", path, params, json_data)
        try:
            resp = await client.post(
                path, params=params, json=json_data, headers=headers
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.TimeoutException:
            logger.error("BusinessServiceClient POST %s 超时", path)
            raise BusinessServiceError("业务服务请求超时", status_code=504)
        except httpx.HTTPStatusError as e:
            logger.error("BusinessServiceClient POST %s HTTP %s: %s", path, e.response.status_code, e.response.text[:200])
            raise BusinessServiceError(
                f"业务服务返回错误: HTTP {e.response.status_code}",
                status_code=e.response.status_code,
            )
        except httpx.RequestError as e:
            logger.error("BusinessServiceClient POST %s 网络异常: %s", path, e)
            raise BusinessServiceError("无法连接业务服务", status_code=502)

    async def _patch(
        self, path: str, 
        params: dict[str, Any] | None = None, 
        json_data: dict[str, Any] | None = None,  
        headers: dict[str, str] | None = None,
    ) -> dict[str, Any]:
        """
        内部 PATCH 请求封装

        Args:
            path: API 路径
            params: URL 查询参数
            json_data: 请求体 JSON

        Returns:
            解析后的 JSON 响应体
        """
        client = await self._get_client()
        logger.debug("BusinessServiceClient PATCH %s params=%s json=%s", path, params, json_data)
        try:
            resp = await client.patch(path, params=params, json=json_data, headers=headers)
            resp.raise_for_status()
            return resp.json()
        except httpx.TimeoutException:
            logger.error("BusinessServiceClient PATCH %s 超时", path)
            raise BusinessServiceError("业务服务请求超时", status_code=504)
        except httpx.HTTPStatusError as e:
            logger.error("BusinessServiceClient PATCH %s HTTP %s: %s", path, e.response.status_code, e.response.text[:200])
            raise BusinessServiceError(
                f"业务服务返回错误: HTTP {e.response.status_code}",
                status_code=e.response.status_code,
            )
        except httpx.RequestError as e:
            logger.error("BusinessServiceClient PATCH %s 网络异常: %s", path, e)
            raise BusinessServiceError("无法连接业务服务", status_code=502)

    # ============================
    # 文件相关 API
    # ============================

    async def get_file_metadata(
        self,
        file_id: str,
        user_id: str,
        space_id: str | None = None,
        space_operation: str = "READ",
    ) -> dict[str, Any]:
        """
        获取文件元数据

        GET business/internal/storage/files/{file_id}?uid={user_id}

        Args:
            file_id: 文件 ID
            user_id: 用户 ID（由网关 X-User-Id 头注入，透传给业务服务做权限校验）

        Returns:
            {
                "code": 200,
                "data": {
                    "storage_path": "...", "name": "...", "size": 123,
                    "type": "...", "node_id": "...", ...
                }
            }

        Raises:
            BusinessServiceError: 文件不存在或服务不可用
        """
        path = BusinessServicePaths.FILE_METADATA.format(file_id=file_id)
        params = {"uid": user_id}
        # 需求五-8：内部调用由被调用方执行空间权限校验，不能只信任网关调用方。
        from app.core.space_context import get_current_space_id
        resolved_space_id = space_id or get_current_space_id()
        headers = {"X-Space-Operation": space_operation.upper()}
        if resolved_space_id:
            headers["X-Space-Id"] = resolved_space_id
        logger.info(
            "获取文件元数据: file_id=%s, user_id=%s, space_id=%s",
            file_id, user_id, resolved_space_id or "personal-default",
        )
        result = await self._get(path, params=params, headers=headers)
        if result.get("code") != 200:
            raise BusinessServiceError(
                "文件不存在或无权访问",
                status_code=404,
                response_body=result,
            )
        return result

    async def resolve_share_resource(
        self,
        share_token: str,
        share_resource_id: str,
        share_access_token: str,
        *,
        operation: str = "READ",
    ) -> dict[str, Any]:
        """
        解析并校验分享资源，返回仅供文件服务内部使用的文件元数据。

        需求二-1/2、三-4：分享虚拟 ID 不在文件服务侧自行解密或猜测，
        由主业务服务校验访问令牌、分享生命周期、资源归属和下载权限；
        这样可避免两个服务实现不同步导致跨分享/跨空间越权。
        """
        path = BusinessServicePaths.SHARE_RESOURCE_ACCESS.format(
            share_token=share_token,
            share_resource_id=share_resource_id,
        )
        result = await self._get(
            path,
            params={"access_token": share_access_token, "operation": operation.upper()},
        )
        if result.get("code") != 200 or not result.get("data"):
            raise BusinessServiceError("分享资源不存在或访问令牌无效", status_code=403, response_body=result)
        return result

    async def get_file_by_node(self, node_id: str, file_name: str, user_id: str) -> dict[str, Any]:
        """
        按节点 ID + 文件名获取文件元数据

        GET business/internal/storage/files/{node_id}/{file_name}?uid={user_id}

        Args:
            node_id: 文件节点 ID
            file_name: 文件名
            user_id: 用户 ID

        Returns:
            {"code": 200, "data": {"storage_path": "...", ...}}

        Raises:
            BusinessServiceError: 文件不存在或无权访问
        """
        path = BusinessServicePaths.FILE_BY_NODE.format(node_id=node_id, file_name=file_name)
        params = {"uid": user_id}
        logger.info("按节点获取文件: node_id=%s, file_name=%s, user_id=%s", node_id, file_name, user_id)
        result = await self._get(path, params=params)
        if result.get("code") != 200:
            raise BusinessServiceError(
                "文件不存在用户网盘, 或者路径目录不存在",
                status_code=404,
                response_body=result,
            )
        return result

    async def notify_file_merged(
        self, uploads_id: str, storage_path: str, file_id: int, user_id: str
    ) -> dict[str, Any]:
        """
        通知业务服务：文件合并完成，更新文件状态记录

        POST business/internal/storage/files

        Args:
            uploads_id: 上传会话 ID
            storage_path: 文件存储路径
            file_id: 文件 ID
            user_id: 用户 ID

        Returns:
            业务服务响应

        Raises:
            BusinessServiceError: 通知失败
        """
        path = BusinessServicePaths.FILE_MERGED
        params = {
            "uploads_id": uploads_id,
            "file_storage_path": storage_path,
            "file_id": file_id,
            "uid": user_id,
        }
        logger.info("通知文件合并完成: uploads_id=%s, file_id=%s", uploads_id, file_id)
        return await self._post(path, params=params)

    async def notify_file_activate(
        self,
        file_id: str,
        user_id: str,
        *,
        storage_path: str | None = None,
        checksum: str | None = None,
        file_size: int | None = None,
        content_revision: int = 0,
        content_modified: bool = False,
        preprocess_status: str = "",
        space_id: str = "",
    ) -> dict[str, Any]:
        """
        通知业务服务：文件处理完毕，标记为活跃状态

        POST business/internal/storage/files/{file_id}/activate

        Args:
            file_id: 文件 ID
            user_id: 用户 ID

        Returns:
            业务服务响应

        Raises:
            BusinessServiceError: 标记失败
        """
        path = BusinessServicePaths.FILE_ACTIVATE.format(file_id=file_id)
        params = {"uid": user_id}
        logger.info("通知文件标记活跃: file_id=%s", file_id)
        headers = {"X-Space-Id": space_id} if space_id else None
        return await self._post(
            path,
            params=params,
            headers=headers,
            json_data={
                "storage_path": storage_path,
                "checksum": checksum,
                "size": file_size,
                "content_revision": content_revision,
                "content_modified": content_modified,
                "preprocess_status": preprocess_status,
            },
        )

    async def notify_file_status(
        self, file_id: str, status: str, user_id: str, error_message: str = ""
    ) -> dict[str, Any]:
        """
        通知业务服务更新文件状态

        PATCH business/internal/storage/files/{file_id}/status

        Args:
            file_id: 文件 ID
            status: 文件状态 (active / merge_failed / dangerous / scan_failed / scanning / merged)
            user_id: 用户 ID
            error_message: 错误信息（可选）

        Returns:
            业务服务响应

        Raises:
            BusinessServiceError: 状态更新失败
        """
        path = BusinessServicePaths.FILE_STATUS.format(file_id=file_id)
        params = {
            "status": status,
            "uid": user_id,
            "error_message": error_message or "",
        }
        logger.info("通知文件状态更新: file_id=%s, status=%s", file_id, status)
        return await self._patch(path, params=params)

    async def notify_file_delete_complete(
        self, file_id: str, deleted_files: list[str], user_id: str
    ) -> dict[str, Any]:
        """
        通知业务服务：文件删除完成

        POST business/internal/storage/files/{file_id}/delete-complete

        Args:
            file_id: 文件 ID
            deleted_files: 已删除的文件路径列表
            user_id: 用户 ID

        Returns:
            业务服务响应

        Raises:
            BusinessServiceError: 通知失败
        """
        path = BusinessServicePaths.FILE_DELETE_COMPLETE.format(file_id=file_id)
        params = {"uid": user_id}
        logger.info("通知文件删除完成: file_id=%s, deleted_count=%d", file_id, len(deleted_files))
        logger.debug("已删除文件列表: %s", deleted_files)
        return await self._post(path, params=params)

    async def notify_hls_ready(
        self, file_id: str, user_id: str, hls_dir: str, resolutions: list[str]
    ) -> dict[str, Any]:
        """
        通知业务服务：HLS 转码完成

        POST business/internal/storage/files/{file_id}/hls-ready

        Args:
            file_id: 文件 ID
            user_id: 用户 ID
            hls_dir: HLS 分片目录
            resolutions: 转码分辨率列表

        Returns:
            业务服务响应

        Raises:
            BusinessServiceError: 通知失败
        """
        path = BusinessServicePaths.FILE_HLS_READY.format(file_id=file_id)
        params = {"uid": user_id}
        json_data = {
            "hls_dir": hls_dir,
            "resolutions": resolutions,
            "has_hls": True,
        }
        logger.info("通知 HLS 转码完成: file_id=%s, resolutions=%s", file_id, resolutions)
        return await self._post(path, params=params, json_data=json_data)

    # ============================
    # 上传会话相关 API
    # ============================

    async def get_upload_session(self, uploads_id: str) -> dict[str, Any]:
        """
        获取上传会话详情

        GET business/internal/storage/uploads/{uploads_id}

        Args:
            uploads_id: 上传会话 ID

        Returns:
            {
                "code": 200,
                "data": {
                    "user_id": "...", "total_chunks": 10, "chunks_max_size": 10485760,
                    "status": "uploading", "file_name": "...", ...
                }
            }
            如果会话不存在，返回 {"code": 15000, ...}

        Raises:
            BusinessServiceError: 服务不可用
        """
        path = BusinessServicePaths.UPLOAD_SESSION.format(uploads_id=uploads_id)
        logger.info("获取上传会话: uploads_id=%s", uploads_id)
        return await self._get(path)

    async def get_chunk_status(self, uploads_id: str, chunk_index: int) -> dict[str, Any]:
        """
        获取分片上传状态

        GET business/internal/storage/uploads/{uploads_id}/chunks/{chunk_index}

        Args:
            uploads_id: 上传会话 ID
            chunk_index: 分片索引（从 1 开始）

        Returns:
            {"code": 200, "data": null} 表示分片未上传
            {"code": 200, "data": {...}} 表示分片已上传

        Raises:
            BusinessServiceError: 服务不可用
        """
        path = BusinessServicePaths.UPLOAD_CHUNK_STATUS.format(
            uploads_id=uploads_id, chunk_index=chunk_index
        )
        logger.info("获取分片状态: uploads_id=%s, chunk_index=%s", uploads_id, chunk_index)
        return await self._get(path)

    async def notify_chunk_complete(
        self, uploads_id: str, chunk_index: int, storage_path: str
    ) -> dict[str, Any]:
        """
        通知业务服务：分片上传完成

        POST business/internal/storage/uploads/{uploads_id}/chunks/{chunk_index}/complete

        Args:
            uploads_id: 上传会话 ID
            chunk_index: 分片索引
            storage_path: 分片存储路径

        Returns:
            业务服务响应

        Raises:
            BusinessServiceError: 通知失败
        """
        path = BusinessServicePaths.UPLOAD_CHUNK_COMPLETE.format(
            uploads_id=uploads_id, chunk_index=chunk_index
        )
        params = {"storage_path": storage_path}
        logger.info("通知分片完成: uploads_id=%s, chunk_index=%s", uploads_id, chunk_index)
        return await self._post(path, params=params)

    async def merge_upload(self, uploads_id: str) -> dict[str, Any]:
        """
        合并上传分片

        POST business/internal/storage/uploads/{uploads_id}/merge

        Args:
            uploads_id: 上传会话 ID

        Returns:
            业务服务响应（包含合并后的文件信息）

        Raises:
            BusinessServiceError: 合并失败
        """
        path = BusinessServicePaths.UPLOAD_MERGE.format(uploads_id=uploads_id)
        logger.info("合并上传分片: uploads_id=%s", uploads_id)
        return await self._post(path)

    async def cleanup_upload_session_after_merge(self, uploads_id: str) -> bool:
        """分块清理完成后删除已完成上传会话及分块元数据，不触发配额回滚。"""
        path = BusinessServicePaths.UPLOAD_MERGE_CLEANUP.format(uploads_id=uploads_id)
        logger.info("通知业务服务清理已完成上传会话: uploads_id=%s", uploads_id)
        try:
            await self._post(path)
            return True
        except BusinessServiceError as e:
            logger.error("清理上传会话失败: uploads_id=%s, error=%s", uploads_id, e)
            return False

    async def notify_upload_delete_complete(self, uploads_id: str) -> bool:
        """
        通知业务服务：上传会话删除完成

        POST business/internal/storage/uploads/{uploads_id}/delete-complete

        Args:
            uploads_id: 上传会话 ID

        Returns:
            True 表示通知成功，False 表示通知失败
            （不抛异常，因为此回调不应阻塞主流程）
        """
        path = BusinessServicePaths.UPLOAD_DELETE_COMPLETE.format(uploads_id=uploads_id)
        logger.info("通知上传会话删除完成: uploads_id=%s", uploads_id)
        try:
            await self._post(path)
            return True
        except BusinessServiceError as e:
            logger.error("通知上传会话删除失败: uploads_id=%s, error=%s", uploads_id, e)
            return False


# ============================
# 自定义异常
# ============================

class BusinessServiceError(Exception):
    """
    业务服务调用异常

    Attributes:
        message: 人类可读的错误描述
        status_code: HTTP 状态码（用于向客户端返回）
        response_body: 业务服务原始响应体（可选，用于调试）
    """

    def __init__(
        self,
        message: str,
        status_code: int = 502,
        response_body: dict[str, Any] | None = None,
    ):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.response_body = response_body

    def __repr__(self) -> str:
        return f"BusinessServiceError(message={self.message!r}, status_code={self.status_code})"


# ============================
# 全局单例
# ============================

# 导出全局客户端实例，供业务代码直接使用
# 使用方式：from app.core.business_service_client import business_service_client
business_service_client = BusinessServiceClient()
