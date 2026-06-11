"""
指数退避重试服务
"""
from __future__ import annotations
import logging
from core.config import settings

logger = logging.getLogger("retry_service")


class RetryService:
    """
    指数退避重试策略

    延迟计算: min(base_delay * 2^(attempt-1), max_delay)
    例如 base=5s: 5s → 10s → 20s → 40s → 80s → 160s → 300s(max)
    """

    def __init__(
        self,
        max_attempts: int | None = None,
        base_delay_seconds: int | None = None,
        max_delay_seconds: int | None = None,
    ):
        self.max_attempts = max_attempts or settings.retry_max_attempts
        self.base_delay = base_delay_seconds or settings.retry_base_delay_seconds
        self.max_delay = max_delay_seconds or settings.retry_max_delay_seconds

    def should_retry(self, retry_count: int, failure_reason: str = "") -> bool:
        """判断是否应该重试"""
        if retry_count >= self.max_attempts:
            return False
        # 某些失败原因不应该重试
        from core.config import FailureReason
        if failure_reason in FailureReason.NO_RETRY_REASONS:
            return False
        return True

    def get_delay_seconds(self, retry_count: int) -> float:
        """
        计算第 N 次重试的延迟秒数
        attempt=1 表示第一次重试
        """
        delay = self.base_delay * (2 ** (retry_count - 1))
        return min(delay, self.max_delay)

    @staticmethod
    def is_fatal(failure_reason: str) -> bool:
        """是否为不可恢复的致命错误（不应重试）"""
        from core.config import FailureReason
        return failure_reason in FailureReason.NO_RETRY_REASONS