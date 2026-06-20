"""
AI Processing Service - 统一日志配置

支持彩色日志输出、JSON 格式 (生产环境)、日志级别动态控制。
"""
import logging
import sys
import os
from typing import Optional


# 日志颜色 (ANSI)
COLORS = {
    "DEBUG": "\033[36m",     # Cyan
    "INFO": "\033[32m",      # Green
    "WARNING": "\033[33m",   # Yellow
    "ERROR": "\033[31m",     # Red
    "CRITICAL": "\033[35m",  # Magenta
    "RESET": "\033[0m",
    "BOLD": "\033[1m",
    "DIM": "\033[2m",
}


class ColoredFormatter(logging.Formatter):
    """带颜色的日志格式化器"""

    def format(self, record: logging.LogRecord) -> str:
        levelname = record.levelname
        if levelname in COLORS:
            record.levelname = (
                f"{COLORS[levelname]}{COLORS['BOLD']}{levelname:<8}{COLORS['RESET']}"
            )
        else:
            record.levelname = f"{levelname:<8}"

        # 模块名加暗色
        record.name = f"{COLORS['DIM']}{record.name}{COLORS['RESET']}"

        return super().format(record)


class JsonFormatter(logging.Formatter):
    """JSON 格式日志 (生产环境)"""

    def format(self, record: logging.LogRecord) -> str:
        import json
        from datetime import datetime, timezone

        log_entry = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "module": record.module,
            "function": record.funcName,
            "line": record.lineno,
        }
        if record.exc_info and record.exc_info[1]:
            log_entry["exception"] = str(record.exc_info[1])

        return json.dumps(log_entry, ensure_ascii=False)


def setup_logging(
    level: int = logging.INFO,
    enable_color: bool = True,
    json_format: bool = False,
) -> None:
    """
    配置全局日志

    Args:
        level: 日志级别
        enable_color: 是否启用彩色输出 (终端)
        json_format: 是否使用 JSON 格式 (生产环境)
    """
    root_logger = logging.getLogger()
    root_logger.setLevel(level)

    # 清除已有 handler
    root_logger.handlers.clear()

    # 创建 handler
    handler = logging.StreamHandler(sys.stdout)
    handler.setLevel(level)

    if json_format:
        formatter = JsonFormatter()
    elif enable_color and sys.stdout.isatty():
        formatter = ColoredFormatter(
            fmt="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S",
        )
    else:
        formatter = logging.Formatter(
            fmt="%(asctime)s | %(levelname)-8s | %(name)-30s | %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S",
        )

    handler.setFormatter(formatter)
    root_logger.addHandler(handler)

    # 降低第三方库日志级别
    for lib in ("aio_pika", "aiormq", "pika", "urllib3", "botocore", "s3transfer"):
        logging.getLogger(lib).setLevel(logging.WARNING)

    # PaddleOCR 日志
    logging.getLogger("ppocr").setLevel(logging.WARNING)
    logging.getLogger("paddle").setLevel(logging.WARNING)

    # transformers 日志
    logging.getLogger("transformers").setLevel(logging.WARNING)


def get_logger(name: str) -> logging.Logger:
    """获取命名 logger"""
    return logging.getLogger(name)