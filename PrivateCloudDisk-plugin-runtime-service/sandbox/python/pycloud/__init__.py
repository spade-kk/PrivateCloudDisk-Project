"""PrivateCloudDisk 云插件唯一平台能力入口。"""

from .context import configure, current_context
from . import file
from .file import read, read_staging, write, write_pre_activation
from .capabilities import (
    _configure_runtime_transport,
    call_api,
    user_info,
    space_members_list,
    notification_send,
    CapabilityError,
    CapabilityTimeout,
)
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
    "file",
    "call_api",
    "user_info",
    "space_members_list",
    "notification_send",
    "CapabilityError",
    "CapabilityTimeout",
    "log",
    "capability",
    "test",
]
