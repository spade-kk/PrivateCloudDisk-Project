"""
Office 文件转 PDF 消费者 — 文件增强阶段

职责: 将 Office 文档（Word/Excel/PPT）转换为 PDF 格式，生成统一的预览资源。
PDF 文件本身则跳过转换，但生成 PDF 首页缩略图。

特性: 并发执行，失败不影响文件可用性，仅标记 DEGRADED。
       继承 BaseEnhanceConsumer，复用幂等检查、重试策略、DLQ 机制。

与 TranscodeConsumer 一致的设计模式:
  - 继承 BaseEnhanceConsumer
  - 实现 stage 属性和 process() 方法
  - 模块级创建单例 + 导出 on_enhance_office_to_pdf_message 回调
"""
from __future__ import annotations
import logging
from core.config import TaskTypes
from core.event.file_enhance_event import FileEnhanceEvent
from core.consumers.enhancement.base_enhance_consumer import BaseEnhanceConsumer
from core.services.file_processor import FileProcessor, ProcessResult

logger = logging.getLogger("office_to_pdf_consumer")


class OfficeToPdfConsumer(BaseEnhanceConsumer):
    """Office 文件转 PDF 消费者 — 文件增强阶段"""

    @property
    def stage(self) -> str:
        return TaskTypes.OFFICE_TO_PDF

    async def process(self, event: FileEnhanceEvent) -> ProcessResult:
        """
        处理 Office 文件转 PDF 增强事件

        流程:
          1. 记录处理开始日志
          2. 调用 FileProcessor._do_office_to_pdf() 执行转换
          3. 记录处理结果日志
          4. 返回 ProcessResult

        Args:
            event: 文件增强事件

        Returns:
            ProcessResult: 处理结果
        """
        logger.info(
            f"[OFFICE_TO_PDF] START "
            f"enhance_task_id={event.enhance_task_id} "
            f"file_id={event.file_id} "
            f"file_type={event.file_type}"
        )

        result = await FileProcessor._do_office_to_pdf(event)

        if result.success:
            skip = result.data.get("skipped", False)
            source_type = result.data.get("source_type", "")
            pdf_size = result.data.get("pdf_size", 0)
            logger.info(
                f"[OFFICE_TO_PDF] DONE "
                f"source_type={source_type} "
                f"pdf_size={pdf_size} "
                f"skipped={skip}"
            )
        else:
            logger.error(
                f"[OFFICE_TO_PDF] FAILED "
                f"reason={result.failure_reason} "
                f"error={result.error}"
            )

        return result


# 模块级单例
office_to_pdf_consumer = OfficeToPdfConsumer()


async def on_enhance_office_to_pdf_message(message):
    """
    Office 转 PDF 增强消息回调

    由 RabbitMQ 消费者调用，传入原始消息对象。
    所有幂等检查、重试策略、DLQ 处理均由 BaseEnhanceConsumer.handle() 管理。

    Args:
        message: aio_pika 消息对象
    """
    await office_to_pdf_consumer.handle(message)