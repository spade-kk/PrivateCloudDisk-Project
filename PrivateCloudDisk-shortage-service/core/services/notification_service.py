"""
通知服务 - 负责与业务服务 (Business Service) 通信

注意：每个请求独立创建 httpx.AsyncClient，避免单例 client
在多个 event loop 间共享导致 "Event loop is closed" 错误。
（Worker 进程的主事件循环 + 线程池中的 asyncio.run() 是两个不同的 event loop）
"""
from __future__ import annotations
import logging
import httpx
from core.config import settings

logger = logging.getLogger("notification_service")

# HTTP 客户端通用配置
_CLIENT_TIMEOUT = httpx.Timeout(30.0)
_CLIENT_LIMITS = httpx.Limits(max_keepalive_connections=10)


class NotificationService:
    """向业务服务发送文件处理状态变更通知"""

    @staticmethod
    async def notify_file_merged(
        uploads_id: str,
        storage_path: str,
        file_id: int,
        user_id: str
    ):
        """
        通知业务服务：文件合并完成，更新文件状态记录
        """
        async with httpx.AsyncClient(
            timeout=_CLIENT_TIMEOUT,
            limits=_CLIENT_LIMITS,
            rust_env=False
        ) as client:
            try:
                resp = await client.post(
                    f"{settings.business_service_url}/api/v1/business/internal/storage/files",
                    params={
                        "uploads_id": uploads_id,
                        "file_storage_path": storage_path,
                        "file_id": file_id,
                        "uid": user_id
                    },
                )
                resp.raise_for_status()
                logger.info(f"文件合并成功状态更新记录成功: file_id={file_id}")
                return file_id
            except Exception as e:
                logger.error(f"文件合并成功状态更新记录失败: file_id={file_id}, error={e}")
                raise

    @staticmethod
    async def notify_file_activate(file_id: str, user_id: str) -> bool:
        """通知业务服务：文件处理完毕，标记为活跃"""
        async with httpx.AsyncClient(
            timeout=_CLIENT_TIMEOUT,
            limits=_CLIENT_LIMITS,
            rust_env=False
        ) as client:
            try:
                resp = await client.post(
                    f"{settings.business_service_url}/api/v1/business/internal/storage/files/{file_id}/activate",
                    params={"uid": user_id},
                )
                resp.raise_for_status()
                logger.info(f"文件已标记为活跃: file_id={file_id}")
                return True
            except Exception as e:
                logger.error(f"标记活跃失败: file_id={file_id}, error={e}")
                raise

    @staticmethod
    async def notify_file_status(
        file_id: str,
        status: str,
        user_id: str,
        error_message: str
    ) -> bool:
        """
        通知业务服务更新文件状态
        status: active / merge_failed / dangrous / scan_failed / scanning / merged
        """
        logger.info(
            f"通知业务服务更新文件状态: file_id={file_id}, status={status}, "
            f"error={error_message or '(none)'}"
        )
        async with httpx.AsyncClient(
            timeout=_CLIENT_TIMEOUT,
            limits=_CLIENT_LIMITS,
            rust_env=False
        ) as client:
            try:
                resp = await client.patch(
                    f"{settings.business_service_url}/api/v1/business/internal/storage/files/{file_id}/status",
                    params={
                        "status": status,
                        "uid": user_id
                    },
                )
                resp.raise_for_status()
                logger.info(f"文件状态已更新: file_id={file_id}, status={status}")
                return True
            except Exception as e:
                logger.error(f"更新文件状态失败: file_id={file_id}, error={e}")
                raise
    @staticmethod
    async def notify_file_delete_complete(
        file_id: str, deleted_files: list, user_id
    ) -> bool:
        """通知业务服务：文件删除完成"""
        async with httpx.AsyncClient(
            timeout=_CLIENT_TIMEOUT,
            limits=_CLIENT_LIMITS,
            rust_env=False
        ) as client:
            try:
                resp = await client.post(
                    f"{settings.business_service_url}/api/v1/business/internal/storage/files/{file_id}/delete-complete",
                    params={"uid": user_id},
                )
                resp.raise_for_status()
                logger.info(f"文件删除通知成功: file_id={file_id}")
                logger.debug(f"总共删除文件: List=[{deleted_files}]")
                return True
            except Exception as e:
                logger.error(f"文件删除通知失败: file_id={file_id}, error={e}")
                raise

    @staticmethod
    async def notify_security_event(
        file_id: str,
        user_id: str,
        threat_name: str,
        action: str,  # "quarantined" / "deleted" / "blocked"
        details: str = "",
    ) -> bool:
        # """通知业务服务：安全事件"""
        # client = await _get_client()
        # try:
        #     resp = await client.post(
        #         f"{settings.business_service_url}/api/v1/business/internal/storage/files/{file_id}/security-event",
        #         json={
        #             "user_id": user_id,
        #             "threat_name": threat_name,
        #             "action": action,
        #             "details": details,
        #         },
        #     )
        #     resp.raise_for_status()
        #     logger.warning(f"安全事件已上报: file_id={file_id}, threat={threat_name}, action={action}")
        return True
        # except Exception as e:
        #     logger.error(f"安全事件上报失败: file_id={file_id}, error={e}")
        #     # 不上抛异常，安全事件上报失败不应阻塞主流程
        #     return False