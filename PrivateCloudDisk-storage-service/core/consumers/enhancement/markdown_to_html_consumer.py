"""
Markdown 文件转 HTML 消费者 — 文件增强阶段

职责: 将 Markdown 文件转换为 HTML 格式，生成统一的预览资源。
增强失败不影响文件可用性，仅标记 DEGRADED。

继承 BaseEnhanceConsumer，复用幂等检查、重试策略、DLQ 机制。

与 OfficeToPdfConsumer 一致的设计模式:
  - 继承 BaseEnhanceConsumer
  - 实现 stage 属性和 process() 方法
  - 模块级创建单例 + 导出 on_enhance_markdown_to_html_message 回调
"""
from __future__ import annotations
import logging
from core.config import TaskTypes
from core.event.file_enhance_event import FileEnhanceEvent
from core.consumers.enhancement.base_enhance_consumer import BaseEnhanceConsumer
from core.services.file_processor import FileProcessor, ProcessResult

logger = logging.getLogger("markdown_to_html_consumer")


class MarkdownToHtmlConsumer(BaseEnhanceConsumer):
    """Markdown 文件转 HTML 消费者 — 文件增强阶段"""

    @property
    def stage(self) -> str:
        return TaskTypes.MARKDOWN_TO_HTML

    @property
    def max_retries(self) -> int:
        """最大重试次数从配置读取"""
        from core.config import settings
        return settings.file_enhance_markdown_to_html_max_retries

    async def process(self, event: FileEnhanceEvent) -> ProcessResult:
        """
        处理 Markdown 转 HTML 增强事件

        流程:
          1. 记录处理开始日志
          2. 调用 FileProcessor._do_markdown_to_html() 执行转换
          3. 记录处理结果日志
          4. 返回 ProcessResult

        Args:
            event: 文件增强事件

        Returns:
            ProcessResult: 处理结果
        """
        logger.info(
            f"[MARKDOWN_TO_HTML] START "
            f"enhance_task_id={event.enhance_task_id} "
            f"file_id={event.file_id} "
            f"file_type={event.file_type}"
        )

        result = await FileProcessor._do_markdown_to_html(event)

        if result.success:
            skip = result.data.get("skipped", False)
            html_size = result.data.get("html_size", 0)
            logger.info(
                f"[MARKDOWN_TO_HTML] DONE "
                f"skipped={skip} "
                f"html_size={html_size}"
            )
        else:
            logger.error(
                f"[MARKDOWN_TO_HTML] FAILED "
                f"reason={result.failure_reason} "
                f"error={result.error}"
            )

        return result


# 模块级单例
markdown_to_html_consumer = MarkdownToHtmlConsumer()


async def on_enhance_markdown_to_html_message(message):
    """
    Markdown 转 HTML 增强消息回调

    由 RabbitMQ 消费者调用，传入原始消息对象。
    所有幂等检查、重试策略、DLQ 处理均由 BaseEnhanceConsumer.handle() 管理。

    Args:
        message: aio_pika 消息对象
    """
    await markdown_to_html_consumer.handle(message)