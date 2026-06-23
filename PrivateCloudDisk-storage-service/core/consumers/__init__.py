from .file_process_consumer import on_file_process_message
from .file_delete_consumer import on_file_delete_message
from .dead_letter_consumer import on_dead_letter_message
from .content_index_consumer import on_content_index_message
from .uploads_session_delete_consumer import UploadsSessionDeleteConsumer

on_uploads_session_delete_message = UploadsSessionDeleteConsumer().handle

__all__ = [
    "on_file_process_message",
    "on_file_delete_message",
    "on_dead_letter_message",
    "on_content_index_message",
    "on_uploads_session_delete_message",
]