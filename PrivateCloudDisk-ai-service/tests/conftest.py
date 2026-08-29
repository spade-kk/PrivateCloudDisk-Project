from __future__ import annotations

import pytest

from app.core.config import Settings


@pytest.fixture
def settings() -> Settings:
    return Settings(
        environment="test",
        allow_unsigned_identity=True,
        identity_shared_secret="test-signing-secret",
        internal_service_token="internal-test-token",
        llm_api_key="provider-test-token",
        capability_hub_url="http://capability-hub.test",
        max_agent_iterations=4,
        max_run_seconds=30,
    )
