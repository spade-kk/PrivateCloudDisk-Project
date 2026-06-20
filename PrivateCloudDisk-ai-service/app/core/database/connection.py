"""
AI Processing Service - MySQL 数据库连接管理

使用 SQLAlchemy 异步引擎 + aiomysql/asyncmy 驱动。
与 platform-service 共用 private_cloud_disk 数据库。
"""
from __future__ import annotations
import logging
from typing import Optional

from sqlalchemy.ext.asyncio import (
    create_async_engine,
    AsyncSession,
    AsyncEngine,
    async_sessionmaker,
)
from sqlalchemy import text

from app.core.config import settings

logger = logging.getLogger("ai_service.database")


class DatabaseManager:
    """
    数据库连接管理器

    特性:
    - 连接池管理 (pool_size=10, max_overflow=20)
    - 自动重连 (pool_pre_ping=True)
    - 优雅关闭
    """

    def __init__(self):
        self._engine: Optional[AsyncEngine] = None
        self._session_factory: Optional[async_sessionmaker[AsyncSession]] = None

    async def connect(self) -> None:
        """初始化数据库连接"""
        logger.info(f"连接 MySQL: {settings.mysql_host}:{settings.mysql_port}/{settings.mysql_database}")

        self._engine = create_async_engine(
            settings.mysql_url_asyncmy,
            pool_size=10,
            max_overflow=20,
            pool_recycle=3600,
            pool_pre_ping=True,
            echo=False,
            connect_args={
                "charset": "utf8mb4",
            },
        )

        self._session_factory = async_sessionmaker(
            self._engine,
            class_=AsyncSession,
            expire_on_commit=False,
        )

        # 验证连接
        await self._verify_connection()
        logger.info("MySQL 连接成功")

    async def _verify_connection(self) -> None:
        """验证数据库连接"""
        try:
            async with self._engine.begin() as conn:
                await conn.execute(text("SELECT 1"))
        except Exception as e:
            logger.error(f"MySQL 连接验证失败: {e}")
            raise

    async def close(self) -> None:
        """关闭数据库连接"""
        if self._engine:
            await self._engine.dispose()
            logger.info("MySQL 连接已关闭")

    def get_session(self) -> AsyncSession:
        """获取数据库会话"""
        if not self._session_factory:
            raise RuntimeError("数据库未初始化，请先调用 connect()")
        return self._session_factory()

    async def execute(self, sql: str, params: dict | None = None) -> None:
        """执行 SQL 语句"""
        async with self.get_session() as session:
            await session.execute(text(sql), params or {})
            await session.commit()

    async def execute_many(self, sql: str, params_list: list[dict]) -> None:
        """批量执行 SQL 语句"""
        async with self.get_session() as session:
            for params in params_list:
                await session.execute(text(sql), params)
            await session.commit()

    async def fetch_all(self, sql: str, params: dict | None = None) -> list[dict]:
        """查询并返回所有结果"""
        async with self.get_session() as session:
            result = await session.execute(text(sql), params or {})
            rows = result.fetchall()
            columns = result.keys()
            return [dict(zip(columns, row)) for row in rows]

    async def fetch_one(self, sql: str, params: dict | None = None) -> dict | None:
        """查询并返回单条结果"""
        async with self.get_session() as session:
            result = await session.execute(text(sql), params or {})
            row = result.fetchone()
            if row is None:
                return None
            return dict(zip(result.keys(), row))


# =============================================================================
# 全局单例
# =============================================================================
db_manager = DatabaseManager()