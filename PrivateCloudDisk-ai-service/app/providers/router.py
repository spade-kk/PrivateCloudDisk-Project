"""Controlled model selection and fallback policy."""

from __future__ import annotations

from app.core.config import Settings
from app.providers.openai_compatible import OpenAICompatibleProvider


class ProviderRouter:
    """Only deployment-approved models can be selected; callers never supply URLs or keys."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def allowed_models(self) -> list[str]:
        return [self._settings.llm_model, *self._settings.fallback_models]

    def select(self, requested_model: str | None) -> OpenAICompatibleProvider:
        if requested_model and requested_model not in self.allowed_models():
            raise ValueError("请求的模型未获授权")
        return OpenAICompatibleProvider(self._settings, requested_model or self._settings.llm_model)

    def fallback_models(self, selected_model: str) -> tuple[str, ...]:
        return tuple(model for model in self.allowed_models() if model != selected_model)
