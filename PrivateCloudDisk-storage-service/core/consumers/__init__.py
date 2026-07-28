# ===== 文件删除消费者 =====
from .file_delete_consumer import on_file_delete_message
from .uploads_session_delete_consumer import UploadsSessionDeleteConsumer
from .dlq.uploads_event_dlq_consumer import on_uploads_event_dlq_message

# ===== Backend — 顺序流水线（每阶段独立队列） =====
from .backend import (
    on_backend_merge_message,
    on_backend_hash_message,
    on_backend_virus_message,
    on_backend_mark_active_message,
)
# Enhancement — 并发流水线（各阶段独立并行） =====
from .enhancement import (
    on_enhance_thumbnail_message,
    on_enhance_transcode_message,
    on_enhance_hls_message,
    on_enhance_index_message,
    on_enhance_office_to_pdf_message,
    on_enhance_archive_parse_message,
)
# DLQ — Backend + Enhancement =====
from .dlq.backend_dlq_consumer import on_backend_dlq_message
from .dlq.enhance_dlq_consumer import on_enhance_dlq_message

on_uploads_session_delete_message = UploadsSessionDeleteConsumer().handle

__all__ = [
    # 文件删除
    "on_file_delete_message",
    "on_uploads_session_delete_message",
    "on_uploads_event_dlq_message",
    # Backend
    "on_backend_merge_message",
    "on_backend_hash_message",
    "on_backend_virus_message",
    "on_backend_mark_active_message",
    # Enhancement
    "on_enhance_thumbnail_message",
    "on_enhance_transcode_message",
    "on_enhance_hls_message",
    "on_enhance_index_message",
    "on_enhance_office_to_pdf_message",
    "on_enhance_archive_parse_message",
    # DLQ
    "on_backend_dlq_message",
    "on_enhance_dlq_message",
]
