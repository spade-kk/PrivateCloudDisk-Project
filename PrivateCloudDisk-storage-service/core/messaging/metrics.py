"""轻量级 Worker 指标聚合器。

指标先在进程内聚合并输出结构化日志，避免强制引入 Prometheus 运行时；后续可将
`snapshot()` 接到现有监控采集器而不改消费者代码。
"""
from __future__ import annotations

import asyncio
import time
from collections import Counter
from dataclasses import dataclass, field
from typing import Any


@dataclass
class WorkerMetrics:
    received: Counter = field(default_factory=Counter)
    succeeded: Counter = field(default_factory=Counter)
    retried: Counter = field(default_factory=Counter)
    dead_lettered: Counter = field(default_factory=Counter)
    failed: Counter = field(default_factory=Counter)
    _started_at: float = field(default_factory=time.monotonic)
    _lock: asyncio.Lock = field(default_factory=asyncio.Lock)

    async def record(self, queue: str, outcome: str) -> None:
        async with self._lock:
            self.received[queue] += 1
            if outcome == "success":
                self.succeeded[queue] += 1
            elif outcome == "retry":
                self.retried[queue] += 1
            elif outcome == "dlq":
                self.dead_lettered[queue] += 1
            else:
                self.failed[queue] += 1

    def snapshot(self) -> dict[str, Any]:
        return {
            "uptime_seconds": round(time.monotonic() - self._started_at, 3),
            "received": dict(self.received),
            "succeeded": dict(self.succeeded),
            "retried": dict(self.retried),
            "dead_lettered": dict(self.dead_lettered),
            "failed": dict(self.failed),
        }


worker_metrics = WorkerMetrics()
