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


def _internal_headers() -> dict[str, str]:
    """Sprint 0：内部回调统一携带服务凭证，禁止借用公网用户身份。"""
    if not settings.pcd_internal_service_token:
        return {}
    return {"X-PCD-Service-Token": settings.pcd_internal_service_token}


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
            trust_env=False,
            headers=_internal_headers(),
        ) as client:
            try:
                resp = await client.post(
                    f"{settings.business_service_url}/business/internal/storage/files",
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
    async def notify_upload_session_merge_cleanup(uploads_id: str) -> bool:
        """分块文件清理完成后删除已完成上传会话，不触发配额回滚。"""
        async with httpx.AsyncClient(
            timeout=_CLIENT_TIMEOUT,
            limits=_CLIENT_LIMITS,
            trust_env=False,
            headers=_internal_headers(),
        ) as client:
            try:
                resp = await client.post(
                    f"{settings.business_service_url}/business/internal/storage/uploads/{uploads_id}/merge-cleanup"
                )
                resp.raise_for_status()
                logger.info("合并后上传会话清理成功: uploads_id=%s", uploads_id)
                return True
            except Exception as e:
                # 分块清理已完成时，通知失败不应重新执行合并；定时清理/运维可再次调用幂等接口。
                logger.error("合并后上传会话清理失败: uploads_id=%s, error=%s", uploads_id, e)
                return False

    @staticmethod
    async def notify_file_activate(
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
    ) -> bool:
        """通知业务服务：原子提交最终内容快照并标记为活跃。"""
        headers = _internal_headers()
        if space_id:
            headers["X-Space-Id"] = space_id
        async with httpx.AsyncClient(
            timeout=_CLIENT_TIMEOUT,
            limits=_CLIENT_LIMITS,
            trust_env=False,
            headers=headers,
        ) as client:
            try:
                resp = await client.post(
                    f"{settings.business_service_url}/business/internal/storage/files/{file_id}/activate",
                    params={"uid": user_id},
                    json={
                        "storage_path": storage_path,
                        "checksum": checksum,
                        "size": file_size,
                        "content_revision": content_revision,
                        "content_modified": content_modified,
                        "preprocess_status": preprocess_status,
                    },
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
            trust_env=False,
            headers=_internal_headers(),
        ) as client:
            try:
                resp = await client.patch(
                    f"{settings.business_service_url}/business/internal/storage/files/{file_id}/status",
                    params={
                        "status": status,
                        "uid": user_id,
                        "error_message": error_message or "",
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
            trust_env=False,
            headers=_internal_headers(),
        ) as client:
            try:
                resp = await client.post(
                    f"{settings.business_service_url}/business/internal/storage/files/{file_id}/delete-complete",
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
    async def notify_hls_transcode_complete(
        file_id: str,
        user_id: str,
        hls_dir: str,
        resolutions: list,
    ) -> bool:
        """通知业务服务：HLS 转码完成"""
        logger.info(
            f"通知 HLS 转码完成: file_id={file_id}, "
            f"resolutions={len(resolutions)}"
        )
        async with httpx.AsyncClient(
            timeout=_CLIENT_TIMEOUT,
            limits=_CLIENT_LIMITS,
            trust_env=False,
            headers=_internal_headers(),
        ) as client:
            try:
                resp = await client.post(
                    f"{settings.business_service_url}/business/internal/storage/files/{file_id}/hls-ready",
                    params={"uid": user_id},
                    json={
                        "hls_dir": hls_dir,
                        "resolutions": resolutions,
                        "has_hls": True,
                    },
                )
                resp.raise_for_status()
                logger.info(f"HLS 转码通知成功: file_id={file_id}")
                return True
            except Exception as e:
                logger.error(f"HLS 转码通知失败: file_id={file_id}, error={e}")
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

    @staticmethod
    async def notify_ops_alert(
        *,
        title: str,
        severity: str,
        details: dict,
    ) -> bool:
        """向可选的企业运维 Webhook 发送结构化告警。

        AUDIT FIX [7.3]（需求一-3）：
        原行为只有本地日志，值班系统无法主动感知增强死信；新行为在配置
        ``OPS_ALERT_WEBHOOK_URL`` 时推送标准 JSON。告警失败不会阻塞 DLQ 最终处置，
        MySQL 死信台账仍是可审计事实源。
        """
        if not settings.ops_alert_webhook_url:
            logger.warning("未配置 OPS_ALERT_WEBHOOK_URL，运维告警仅记录日志: %s", title)
            return False
        try:
            async with httpx.AsyncClient(
                timeout=httpx.Timeout(8.0),
                limits=_CLIENT_LIMITS,
                trust_env=False,
            ) as client:
                response = await client.post(
                    settings.ops_alert_webhook_url,
                    json={
                        "source": "PrivateCloudDisk-storage-service",
                        "title": title,
                        "severity": severity,
                        "details": details,
                    },
                )
                response.raise_for_status()
            return True
        except Exception as exc:
            logger.error("运维告警发送失败: title=%s, error=%s", title, exc)
            return False
