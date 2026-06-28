from .merge_consumer import on_backend_merge_message
from .hash_consumer import on_backend_hash_message
from .virus_consumer import on_backend_virus_message
from .mark_active_consumer import on_backend_mark_active_message

__all__ = [
    "on_backend_merge_message",
    "on_backend_hash_message",
    "on_backend_virus_message",
    "on_backend_mark_active_message",
]