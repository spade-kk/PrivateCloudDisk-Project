"""
压缩包目录解析消费者 — 文件增强阶段

职责: 解析压缩包文件（ZIP/RAR/7Z/ISO/TAR/GZ/BZ2等），提取目录结构信息，
生成 JSON 格式目录树供前端预览。仅解析目录，不解压完整文件内容。

特性: 并发执行，失败不影响文件可用性，仅标记 DEGRADED。
       继承 BaseEnhanceConsumer，复用幂等检查、重试策略、DLQ 机制。

与 OfficeToPdfConsumer 一致的设计模式:
  - 继承 BaseEnhanceConsumer
  - 实现 stage 属性和 process() 方法
  - 模块级创建单例 + 导出 on_enhance_archive_parse_message 回调
"""
from __future__ import annotations
import logging
from core.config import TaskTypes, FailureReason
from core.event.file_enhance_event import FileEnhanceEvent
from core.consumers.enhancement.base_enhance_consumer import BaseEnhanceConsumer
from core.services.file_processor import FileProcessor, ProcessResult

logger = logging.getLogger("archive_parse_consumer")


class ArchiveParseConsumer(BaseEnhanceConsumer):
    """压缩包目录解析消费者 — 文件增强阶段"""

    @property
    def stage(self) -> str:
        return TaskTypes.ARCHIVE_PARSE

    async def process(self, event: FileEnhanceEvent) -> ProcessResult:
        """
        处理压缩包目录解析增强事件

        流程:
          1. 记录处理开始日志
          2. 调用 FileProcessor._do_archive_parse() 执行解析
          3. 记录处理结果日志
          4. 返回 ProcessResult

        Args:
            event: 文件增强事件

        Returns:
            ProcessResult: 处理结果
        """
        logger.info(
            f"[ARCHIVE_PARSE] START "
            f"enhance_task_id={event.enhance_task_id} "
            f"file_id={event.file_id} "
            f"file_type={event.file_type}"
        )

        result = await FileProcessor._do_archive_parse(event)

        if result.success:
            total_files = result.data.get("total_files", 0)
            total_dirs = result.data.get("total_dirs", 0)
            total_size = result.data.get("total_size", 0)
            logger.info(
                f"[ARCHIVE_PARSE] DONE "
                f"total_files={total_files} "
                f"total_dirs={total_dirs} "
                f"total_size={total_size}"
            )
        else:
            logger.error(
                f"[ARCHIVE_PARSE] FAILED "
                f"reason={result.failure_reason} "
                f"error={result.error}"
            )

        return result


# 模块级单例
archive_parse_consumer = ArchiveParseConsumer()


async def on_enhance_archive_parse_message(message):
    """
    压缩包目录解析增强消息回调

    由 RabbitMQ 消费者调用，传入原始消息对象。
    所有幂等检查、重试策略、DLQ 处理均由 BaseEnhanceConsumer.handle() 管理。

    Args:
        message: aio_pika 消息对象
    """
    await archive_parse_consumer.handle(message)