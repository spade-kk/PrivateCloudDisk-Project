"""结构化插件日志；Runtime 会统一限长和脱敏。"""

from __future__ import annotations

import json


class StructuredLogger:
    """兼容 ``pycloud.log(...)`` 与 ``pycloud.log.info(...)`` 的结构化日志器。"""

    def __call__(self, message: str, **fields) -> None:
        self.info(message, fields or None)

    def _write(self, level: str, message: str, fields: dict | None = None) -> None:
        safe_fields = {
            str(key)[:64]: str(value)[:512]
            for key, value in (fields or {}).items()
        }
        print(json.dumps(
            {
                "level": level,
                "message": str(message)[:1000],
                "fields": safe_fields,
            },
            ensure_ascii=False,
            separators=(",", ":"),
        ))

    def info(self, message: str, fields: dict | None = None) -> None:
        self._write("info", message, fields)

    def warning(self, message: str, fields: dict | None = None) -> None:
        self._write("warning", message, fields)

    def error(self, message: str, fields: dict | None = None) -> None:
        self._write("error", message, fields)


log = StructuredLogger()
