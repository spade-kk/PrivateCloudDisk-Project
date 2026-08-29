"""Regression guard for the no-direct-enterprise-asset boundary."""

from __future__ import annotations

import re
from pathlib import Path


APP_ROOT = Path(__file__).resolve().parents[1] / "app"


def test_agent_runtime_has_no_direct_database_broker_or_object_store_imports():
    source = "\n".join(path.read_text(encoding="utf-8") for path in APP_ROOT.rglob("*.py"))
    forbidden_imports = (
        "sqlalchemy", "aiomysql", "pymysql", "psycopg", "pika", "aio_pika",
        "boto3", "minio", "opensearchpy", "elasticsearch", "motor",
    )
    for package in forbidden_imports:
        assert not re.search(rf"^\s*(?:from|import)\s+{re.escape(package)}(?:\.|\s|$)", source, re.MULTILINE)


def test_only_the_fixed_capability_client_uses_internal_http_transport():
    httpx_files = [path.relative_to(APP_ROOT).as_posix() for path in APP_ROOT.rglob("*.py") if "httpx" in path.read_text(encoding="utf-8")]
    assert httpx_files == ["tools/capability_hub.py"]
