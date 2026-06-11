"""
通知服务 - 负责与业务服务 (Business Service) 通信
"""
from __future__ import annotations
import logging
from typing import Optional
import httpx
from core.config import settings

logger = logging.getLogger("notification_service")

# 全局共享 http 客户端
_client: Optional[httpx.AsyncClient] = None


async def _get_client() -> httpx.AsyncClient:
    global _client
    if _client is None:
        _client = httpx.AsyncClient(
            timeout=httpx.Timeout(30.0),
            limits=httpx.Limits(max_keepalive_connections=10),
        )
    return _client


class NotificationService:
    """向业务服务发送文件处理状态变更通知"""

    @staticmethod
    async def notify_file_merged(
        uploads_id: str,
        storage_path: str,
        file_id: int,
        user_id: str
    ) -> Optional[str]:
        """
        通知业务服务：文件合并完成，创建文件记录
        返回: file_id (业务服务生成)
        """
        client = await _get_client()
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
            logger.info(f"文件记录创建成功: file_id={file_id}")
            return file_id
        except Exception as e:
            logger.error(f"通知业务服务创建文件记录失败: uploads_id={uploads_id}, error={e}")
            raise

    @staticmethod
    async def notify_file_activate(file_id: str, user_id: str) -> bool:
        """通知业务服务：文件处理完毕，标记为活跃"""
        client = await _get_client()
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
        error_message: str = "",
        thumbnails: list | None = None,
        transcoded: list | None = None,
    ) -> bool:
        # """
        # 通知业务服务更新文件状态
        # status: active / degraded / failed
        # """
        # client = await _get_client()
        # try:
        #     resp = await client.patch(
        #         f"{settings.business_service_url}/api/v1/business/internal/storage/files/{file_id}/status",
        #         json={
        #             "status": status,
        #             "error_message": error_message,
        #             "thumbnail_paths": thumbnails or [],
        #             "transcoded_paths": transcoded or [],
        #         },
        #     )
        #     resp.raise_for_status()
        #     logger.info(f"文件状态已更新: file_id={file_id}, status={status}")
        #     return True
        # except Exception as e:
        #     logger.error(f"更新文件状态失败: file_id={file_id}, error={e}")
        #     raise
        return True

    @staticmethod
    async def notify_file_delete_complete(file_id: str, deleted_files: list) -> bool:
        """通知业务服务：文件删除完成"""
        client = await _get_client()
        try:
            resp = await client.post(
                f"{settings.business_service_url}/api/v1/business/internal/storage/files/{file_id}/delete-complete",
                json={"deleted_files": deleted_files},
            )
            resp.raise_for_status()
            logger.info(f"文件删除通知成功: file_id={file_id}")
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
        """通知业务服务：安全事件"""
        client = await _get_client()
        try:
            resp = await client.post(
                f"{settings.business_service_url}/api/v1/business/internal/storage/files/{file_id}/security-event",
                json={
                    "user_id": user_id,
                    "threat_name": threat_name,
                    "action": action,
                    "details": details,
                },
            )
            resp.raise_for_status()
            logger.warning(f"安全事件已上报: file_id={file_id}, threat={threat_name}, action={action}")
            return True
        except Exception as e:
            logger.error(f"安全事件上报失败: file_id={file_id}, error={e}")
            # 不上抛异常，安全事件上报失败不应阻塞主流程
            return False