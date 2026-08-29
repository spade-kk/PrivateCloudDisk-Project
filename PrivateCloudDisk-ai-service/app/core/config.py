"""Typed, fail-closed configuration for the Cloud AI Agent Runtime."""

from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict
import os


class Settings(BaseSettings):
    """Deployment settings.

    [AI-AGENT-REBUILD-001] The old AI worker exposed direct database, object-store,
    message-broker and host-path settings. They intentionally do not exist here: the
    only enterprise service URL accepted by this runtime is Capability Hub.
    """

    model_config = SettingsConfigDict(env_prefix="AI_AGENT_", case_sensitive=False)

    environment: Literal["development", "test", "staging", "production"] = "development"
    port: int = Field(default=8001, ge=1, le=65535)
    enable_docs: bool = False
    redis_url: str = "redis://localhost:6379/5"
    capability_hub_url: str = "http://localhost:8087"
    internal_service_token: SecretStr = SecretStr("test")
    identity_shared_secret: SecretStr = SecretStr("replace-with-long-random-secret")
    allow_unsigned_identity: bool = True
    identity_max_age_seconds: int = Field(default=60, ge=5, le=300)

    llm_base_url: str = "https://llm-api.arkcat.cn/v1"
    llm_api_key: SecretStr = SecretStr(os.getenv("REMOTE_STUDIO_AUTH_TOKEN"))
    llm_model: str = "MiniMax"
    llm_fallback_models: str = "MiniMax"
    llm_timeout_seconds: float = Field(default=45.0, ge=1, le=300)
    max_provider_concurrency: int = Field(default=32, ge=1, le=500)
    max_agent_iterations: int = Field(default=20, ge=1, le=32)
    max_run_seconds: int = Field(default=120, ge=5, le=3600)
    max_tool_concurrency: int = Field(default=4, ge=1, le=32)
    max_tool_attempts: int = Field(default=3, ge=1, le=5)
    max_context_chars: int = Field(default=120_000, ge=8_000, le=500_000)
    max_tool_result_bytes: int = Field(default=262_144, ge=1024, le=1_048_576)
    max_message_chars: int = Field(default=32_000, ge=1_000, le=200_000)
    run_rate_limit_per_minute: int = Field(default=20, ge=1, le=600)
    sse_heartbeat_seconds: int = Field(default=15, ge=5, le=60)
    enable_workflow_tools: bool = True
    enable_plugin_tools: bool = True
    allowed_origins: str = ""

    @field_validator("capability_hub_url", "llm_base_url")
    @classmethod
    def strip_trailing_slash(cls, value: str) -> str:
        return value.rstrip("/")

    @model_validator(mode="after")
    def validate_security(self) -> "Settings":
        if self.environment == "production":
            if self.allow_unsigned_identity:
                raise ValueError("AI_AGENT_ALLOW_UNSIGNED_IDENTITY cannot be enabled in production")
            if not self.identity_shared_secret.get_secret_value():
                raise ValueError("AI_AGENT_IDENTITY_SHARED_SECRET is required in production")
            if not self.internal_service_token.get_secret_value():
                raise ValueError("AI_AGENT_INTERNAL_SERVICE_TOKEN is required in production")
            if not self.llm_api_key.get_secret_value():
                raise ValueError("AI_AGENT_LLM_API_KEY is required in production")
        return self

    @property
    def fallback_models(self) -> tuple[str, ...]:
        return tuple(item.strip() for item in self.llm_fallback_models.split(",") if item.strip())

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.allowed_origins.split(",") if origin.strip()]


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
