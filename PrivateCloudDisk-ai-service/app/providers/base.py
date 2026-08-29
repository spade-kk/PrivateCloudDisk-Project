"""Provider-neutral stream contracts."""

from __future__ import annotations

from collections.abc import AsyncIterator, Awaitable, Callable
from dataclasses import dataclass, field
from typing import Any, Protocol


@dataclass(slots=True)
class ProviderDelta:
    content: str = ""
    tool_call_deltas: list[dict[str, Any]] = field(default_factory=list)
    finish_reason: str | None = None
    usage: dict[str, int] | None = None


class LLMProvider(Protocol):
    """Provider interface; all implementations expose the same internal message shape."""

    model: str

    async def stream_chat(
        self,
        *,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
        tool_choice: Any = None,
        cancellation_check: Callable[[], Awaitable[bool]],
    ) -> AsyncIterator[ProviderDelta]: ...

    async def healthcheck(self) -> bool: ...
