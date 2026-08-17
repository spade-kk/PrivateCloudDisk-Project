"""PrivateCloudDisk 云插件唯一平台能力入口。"""

from .context import configure, current_context
from . import file
from .file import read, read_staging, write, write_pre_activation
from .logging_api import log
from .decorators import capability, test

__all__ = [
    "configure",
    "current_context",
    "file",
    "read",
    "read_staging",
    "write",
    "write_pre_activation",
    "log",
    "capability",
    "test",
]
