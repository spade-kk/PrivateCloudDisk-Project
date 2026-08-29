"""Single AsyncOpenAI transport for OpenAI, DeepSeek and local compatible servers."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator, Callable
from typing import Any

from openai import APIConnectionError, APIStatusError, APITimeoutError, AsyncOpenAI

from app.core.config import Settings
from app.providers.base import ProviderDelta


class ProviderUnavailableError(RuntimeError):
    """A sanitized provider failure suitable for Agent Runtime error classification."""


class OpenAICompatibleProvider:
    """Adapter with no provider-specific wire protocol.

    DeepSeek, OpenAI, Ollama, vLLM and LMDeploy all use this adapter when configured
    with an OpenAI-compatible base URL. This preserves streaming/tool-call behavior
    and avoids proliferating model-vendor clients through the business runtime.
    """

    def __init__(self, settings: Settings, model: str | None = None) -> None:
        self.model = model or settings.llm_model
        self._client = AsyncOpenAI(
            base_url=settings.llm_base_url,
            api_key=settings.llm_api_key.get_secret_value() or "not-configured",
            timeout=settings.llm_timeout_seconds,
            max_retries=0,
        )
        self._semaphore = asyncio.Semaphore(settings.max_provider_concurrency)

    async def stream_chat(
        self,
        *,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
        tool_choice: Any = None,
        cancellation_check: Callable[[], Any],
    ) -> AsyncIterator[ProviderDelta]:
        try:
            async with self._semaphore:
                stream = await self._client.chat.completions.create(
                    model=self.model,
                    messages=messages,  # type: ignore[arg-type]
                    tools=tools or None,  # type: ignore[arg-type]
                    tool_choice=tool_choice if tools and tool_choice is not None else ("auto" if tools else None),
                    stream=True,
                    stream_options={"include_usage": True},
                )
                async for chunk in stream:
                    if await cancellation_check():
                        await stream.close()
                        return
                    if not chunk.choices:
                        usage = chunk.usage
                        if usage:
                            yield ProviderDelta(usage={
                                "prompt_tokens": usage.prompt_tokens or 0,
                                "completion_tokens": usage.completion_tokens or 0,
                                "total_tokens": usage.total_tokens or 0,
                            })
                        continue
                    choice = chunk.choices[0]
                    delta = choice.delta
                    tool_calls: list[dict[str, Any]] = []
                    for tool_call in delta.tool_calls or []:
                        tool_calls.append({
                            "index": tool_call.index,
                            "id": tool_call.id,
                            "name": tool_call.function.name if tool_call.function else None,
                            "arguments": tool_call.function.arguments if tool_call.function else "",
                        })
                    yield ProviderDelta(
                        content=delta.content or "",
                        tool_call_deltas=tool_calls,
                        finish_reason=choice.finish_reason,
                    )
        except (APITimeoutError, APIConnectionError) as error:
            raise ProviderUnavailableError("模型服务暂时不可用，请稍后重试") from error
        except APIStatusError as error:
            if error.status_code >= 500 or error.status_code == 429:
                raise ProviderUnavailableError("模型服务暂时不可用，请稍后重试") from error
            raise ProviderUnavailableError("模型拒绝了本次请求") from error

    async def healthcheck(self) -> bool:
        # Provider APIs do not consistently expose a harmless health endpoint. Configuration
        # is checked locally; the first actual call reports a sanitized availability failure.
        return bool(self.model)

    async def close(self) -> None:
        await self._client.close()
