"""
病毒扫描服务
支持 ClamAV (pyclamd Unix Socket / TCP) 和 ClamAV CLI (clamscan)
"""
from __future__ import annotations
import logging
import os
import asyncio
import subprocess
from dataclasses import dataclass
from typing import Optional
from core.config import settings, FailureReason

logger = logging.getLogger("virus_scanner")


@dataclass
class VirusScanResult:
    success: bool                       # 扫描是否成功执行
    infected: bool = False              # 是否发现病毒
    threat_name: str = ""               # 病毒/木马名称
    scanner_output: str = ""            # 扫描器原始输出
    failure_reason: str = ""            # 失败原因 (FailureReason)
    skipped: bool = False               # 是否因扫描器不可用而跳过
    skipped_reason: str = ""            # 跳过原因


class VirusScanner:
    """病毒扫描器，支持 ClamAV daemon (pyclamd) 和 CLI 回退"""

    def __init__(self):
        self._cd = None
        self._clamd_available: Optional[bool] = None

    async def _ensure_clamd(self) -> bool:
        """尝试连接 clamd 守护进程"""
        if self._clamd_available is not None:
            return self._clamd_available

        try:
            import pyclamd
            # 尝试 Unix Socket 连接
            socket_paths = [
                "/opt/homebrew/var/run/clamav/clamd.sock",
                "/var/run/clamav/clamd.sock",
                "/tmp/clamd.sock",
            ]
            self._cd = None
            for sock_path in socket_paths:
                if os.path.exists(sock_path):
                    try:
                        cd = pyclamd.ClamdUnixSocket(sock_path)
                        if cd.ping():
                            self._cd = cd
                            self._clamd_available = True
                            logger.info(f"已连接 clamd (Unix Socket): {sock_path}")
                            return True
                    except Exception:
                        continue

            # 回退到 TCP
            try:
                cd = pyclamd.ClamdNetworkSocket('127.0.0.1', 3310)
                if cd.ping():
                    self._cd = cd
                    self._clamd_available = True
                    logger.info("已连接 clamd (TCP: 127.0.0.1:3310)")
                    return True
            except Exception:
                pass

            self._clamd_available = False
            logger.warning("clamd 守护进程不可用")
            return False

        except ImportError:
            self._clamd_available = False
            logger.warning("pyclamd 未安装")
            return False

    async def scan_file(self, file_path: str) -> VirusScanResult:
        """
        扫描单个文件

        处理流程:
        1. 优先使用 clamd 守护进程 (pyclamd)
        2. 回退到 clamscan CLI
        3. 都不行则根据 fail_open 配置决定是否放行

        Args:
            file_path: 文件绝对路径

        Returns:
            VirusScanResult: 扫描结果
        """
        if not settings.virus_scan_enabled:
            return VirusScanResult(
                success=True, skipped=True, skipped_reason="病毒扫描已禁用"
            )

        file_path = os.path.abspath(file_path)
        if not os.path.exists(file_path):
            return VirusScanResult(
                success=False,
                failure_reason=FailureReason.UNKNOWN,
                scanner_output="文件不存在"
            )

        # 策略 1: clamd 守护进程
        if await self._ensure_clamd():
            return await self._scan_with_clamd(file_path)

        # 策略 2: clamscan CLI
        if await self._clamscan_available():
            return await self._scan_with_clamscan(file_path)

        # 策略 3: 扫描器不可用
        if settings.virus_scan_fail_open:
            logger.warning("病毒扫描器不可用，fail-open 模式：放行文件")
            return VirusScanResult(
                success=True, skipped=True,
                skipped_reason="扫描器不可用 (fail-open)"
            )
        else:
            logger.error("病毒扫描器不可用，fail-close 模式：拒绝文件")
            return VirusScanResult(
                success=False,
                failure_reason=FailureReason.VIRUS_SCANNER_UNAVAILABLE,
                scanner_output="扫描器不可用"
            )

    async def _scan_with_clamd(self, file_path: str) -> VirusScanResult:
        """使用 clamd 守护进程扫描"""
        try:
            scan_result = await asyncio.to_thread(self._cd.scan_file, file_path)

            if scan_result is None:
                # None = 无病毒
                return VirusScanResult(success=True, infected=False)

            threat_name = scan_result.get(file_path, ("unknown", ""))[0]
            logger.warning(f"⚠ 发现威胁: {threat_name} in {file_path}")
            return VirusScanResult(
                success=True,
                infected=True,
                threat_name=threat_name,
                scanner_output=str(scan_result),
                failure_reason=FailureReason.VIRUS_FOUND,
            )

        except Exception as e:
            logger.error(f"clamd 扫描异常: {e}")
            return VirusScanResult(
                success=False,
                failure_reason=FailureReason.VIRUS_SCANNER_ERROR,
                scanner_output=str(e),
            )

    async def _scan_with_clamscan(self, file_path: str) -> VirusScanResult:
        """使用 clamscan 命令行工具扫描"""
        try:
            proc = await asyncio.create_subprocess_exec(
                "clamscan", "--stdout", "--no-summary", file_path,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            stdout, stderr = await asyncio.wait_for(
                proc.communicate(), timeout=300
            )
            stdout = stdout.decode("utf-8", errors="replace")
            stderr = stderr.decode("utf-8", errors="replace")

            if proc.returncode == 0:
                return VirusScanResult(success=True, infected=False)
            elif proc.returncode == 1:
                # 发现病毒，解析病毒名
                threat_name = self._parse_clamscan_result(stdout, file_path)
                logger.warning(f"⚠ clamscan 发现威胁: {threat_name} in {file_path}")
                return VirusScanResult(
                    success=True,
                    infected=True,
                    threat_name=threat_name,
                    scanner_output=stdout,
                    failure_reason=FailureReason.VIRUS_FOUND,
                )
            else:
                return VirusScanResult(
                    success=False,
                    failure_reason=FailureReason.VIRUS_SCANNER_ERROR,
                    scanner_output=stderr or stdout,
                )

        except asyncio.TimeoutError:
            return VirusScanResult(
                success=False,
                failure_reason=FailureReason.VIRUS_SCANNER_ERROR,
                scanner_output="扫描超时 (300s)",
            )
        except FileNotFoundError:
            return VirusScanResult(
                success=False,
                failure_reason=FailureReason.VIRUS_SCANNER_UNAVAILABLE,
                scanner_output="clamscan 未安装",
            )
        except Exception as e:
            return VirusScanResult(
                success=False,
                failure_reason=FailureReason.VIRUS_SCANNER_ERROR,
                scanner_output=str(e),
            )

    @staticmethod
    async def _clamscan_available() -> bool:
        """检查 clamscan CLI 是否可用"""
        try:
            proc = await asyncio.create_subprocess_exec(
                "clamscan", "--version",
                stdout=asyncio.subprocess.DEVNULL,
                stderr=asyncio.subprocess.DEVNULL,
            )
            await proc.wait()
            return proc.returncode == 0
        except FileNotFoundError:
            return False

    @staticmethod
    def _parse_clamscan_result(output: str, file_path: str) -> str:
        """从 clamscan 输出解析病毒名"""
        for line in output.strip().split("\n"):
            if file_path in line:
                parts = line.split(":", 1)
                if len(parts) >= 2:
                    threat = parts[1].strip()
                    # 移除 "FOUND" 后缀
                    if threat.upper().endswith(" FOUND"):
                        threat = threat[:-6].strip()
                    return threat
        return "Unknown.Threat"


# 全局单例
virus_scanner = VirusScanner()