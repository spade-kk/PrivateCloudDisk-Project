"""Centralized redaction before model context, event streams or logs."""

from __future__ import annotations

import re
from typing import Any

SENSITIVE_KEY = re.compile(r"(?:api[_-]?key|authorization|password|secret|token|cookie|private[_-]?key|access[_-]?key)", re.IGNORECASE)
ABSOLUTE_PATH = re.compile(r"(?:^|\s)(?:/[\w./-]+|[A-Za-z]:\\[^\s]+)")


def redact(value: Any) -> Any:
    if isinstance(value, dict):
        return {str(key): "***" if SENSITIVE_KEY.search(str(key)) else redact(item) for key, item in value.items()}
    if isinstance(value, list):
        return [redact(item) for item in value]
    if isinstance(value, tuple):
        return tuple(redact(item) for item in value)
    if isinstance(value, str):
        return ABSOLUTE_PATH.sub(" [redacted-path]", value)
    return value


def clamp_text(value: Any, limit: int) -> str:
    text = str(value)
    return text if len(text) <= limit else f"{text[:limit]}…[truncated]"
