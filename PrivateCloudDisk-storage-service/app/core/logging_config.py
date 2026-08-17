"""
日志配置模块
提供统一的日志格式和颜色输出
"""
import logging
import sys
import os
from typing import Optional


# 颜色代码
class Colors:
    RESET = "\033[0m"
    BLACK = "\033[30m"
    RED = "\033[31m"
    GREEN = "\033[32m"
    YELLOW = "\033[33m"
    BLUE = "\033[34m"
    MAGENTA = "\033[35m"
    CYAN = "\033[36m"
    WHITE = "\033[37m"
    BOLD = "\033[1m"
    UNDERLINE = "\033[4m"


class ColoredFormatter(logging.Formatter):
    """
    带颜色的日志格式化器
    """
    
    # 日志级别颜色映射
    LEVEL_COLORS = {
        logging.DEBUG: Colors.BLUE,
        logging.INFO: Colors.GREEN,
        logging.WARNING: Colors.YELLOW,
        logging.ERROR: Colors.RED,
        logging.CRITICAL: f"{Colors.BOLD}{Colors.RED}",
    }
    
    def format(self, record):
        # 创建record的副本，避免修改原始record
        record = logging.makeLogRecord(record.__dict__)
        
        # 获取日志级别颜色
        level_color = self.LEVEL_COLORS.get(record.levelno, Colors.WHITE)
        
        # 设置日志级别名称的颜色
        record.levelname = f"{level_color}{record.levelname}{Colors.RESET}"
        
        # 设置模块名称的颜色
        record.name = f"{Colors.CYAN}{record.name}{Colors.RESET}"
        # 当前Worker颜色
        record.worker_id = f"{Colors.YELLOW}{record.worker_id}"
        
        return super().format(record)


def setup_logging(
    level: int = logging.INFO,
    format_str: Optional[str] = None,
    enable_color: bool = True
) -> None:
    """
    配置日志系统
    
    Args:
        level: 日志级别，默认为 INFO
        format_str: 日志格式字符串，默认为 None（使用默认格式）
        enable_color: 是否启用颜色输出，默认为 True
    """
    # 默认日志格式
    if format_str is None:
        format_str = (
            "%(asctime)s | %(levelname)-8s | worker=%(worker_id)s | %(name)-20s | "
            "L%(lineno)-4d | %(message)s"
        )

    class _WorkerContextFilter(logging.Filter):
        """为多进程日志补齐稳定 worker_id；不改变现有业务日志文本。"""

        def filter(self, record: logging.LogRecord) -> bool:
            record.worker_id = os.getenv("WORKER_ID", f"pid-{os.getpid()}")
            return True
    
    # 创建格式化器
    if enable_color and sys.stdout.isatty():
        formatter = ColoredFormatter(format_str)
    else:
        formatter = logging.Formatter(format_str)
    
    # 获取根日志记录器
    root_logger = logging.getLogger()
    root_logger.setLevel(level)
    
    # 移除所有已存在的处理器（避免重复）
    for handler in root_logger.handlers[:]:
        root_logger.removeHandler(handler)
    
    # 创建控制台处理器
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(level)
    console_handler.setFormatter(formatter)
    console_handler.addFilter(_WorkerContextFilter())
    
    # 添加处理器
    root_logger.addHandler(console_handler)
    
    # 设置第三方库的日志级别
    logging.getLogger("aio_pika").setLevel(logging.WARNING)
    logging.getLogger("aiormq").setLevel(logging.WARNING)
    logging.getLogger("uvicorn").setLevel(logging.INFO)
    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)
    # 抑制 OpenSearch 库在服务不可用时的噪音日志
    logging.getLogger("opensearch").setLevel(logging.ERROR)
    logging.getLogger("opensearchpy").setLevel(logging.ERROR)


def get_logger(name: str) -> logging.Logger:
    """
    获取指定名称的日志记录器
    
    Args:
        name: 日志记录器名称
    
    Returns:
        配置好的日志记录器
    """
    return logging.getLogger(name)
