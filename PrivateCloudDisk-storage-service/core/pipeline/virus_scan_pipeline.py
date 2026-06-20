"""
病毒扫描流水线
扫描文件是否包含病毒/木马，发现威胁则立即隔离
"""
from __future__ import annotations
import logging
from dataclasses import dataclass
from core.config import FailureReason
from core.security.virus_scanner import virus_scanner
from core.security.quarantine_manager import quarantine_manager
from core.security.security_reporter import security_reporter
from core.services.notification_service import NotificationService

logger = logging.getLogger("virus_scan_pipeline")


@dataclass
class VirusScanResult:
    success: bool               # 扫描是否通过 (无病毒 = True)
    infected: bool = False
    threat_name: str = ""
    quarantine_path: str = ""
    file_hash: str = ""
    skipped: bool = False
    skipped_reason: str = ""
    error: str = ""
    failure_reason: str = ""


class VirusScanPipeline:
    """病毒扫描处理流水线"""

    @staticmethod
    async def execute(
        file_id: str,
        user_id: str,
        storage_path: str,
        file_name: str,
        file_type: str = "",
        file_size: int = 0,
        task_id: str = "",
    ) -> VirusScanResult:
        """
        执行病毒扫描

        处理逻辑:
        1. 扫描文件
        2. 无病毒 → 通过
        3. 发现病毒 → 立即隔离 + 生成安全报告 + 通知业务服务
        4. 扫描器异常 → 根据 fail_open 配置决定
        """
        logger.info(f"开始病毒扫描: file_id={file_id}, file_name={file_name}")

        scan_result = await virus_scanner.scan_file(storage_path)

        # 情况 1: 扫描跳过
        if scan_result.skipped:
            return VirusScanResult(
                success=True,
                skipped=True,
                skipped_reason=scan_result.skipped_reason,
            )

        # 情况 2: 扫描通过
        if scan_result.success and not scan_result.infected:
            logger.info(f"病毒扫描通过: file_id={file_id}")
            return VirusScanResult(success=True, infected=False)

        # 情况 3: 发现病毒/木马 → 立即隔离，不重试
        if scan_result.infected:
            logger.critical(f"⚠ 发现恶意文件: {scan_result.threat_name}, file_id={file_id}")

            try:
                # 3a. 隔离文件
                quarantine_info = await quarantine_manager.quarantine_file(
                    file_path=storage_path,
                    file_id=file_id,
                    user_id=user_id,
                    threat_name=scan_result.threat_name,
                    scanner_output=scan_result.scanner_output,
                )

                # 3b. 生成安全报告
                import uuid
                event_id = uuid.uuid4().hex
                await security_reporter.generate_threat_report(
                    event_id=event_id,
                    file_id=file_id,
                    user_id=user_id,
                    file_name=file_name,
                    file_type=file_type,
                    file_size=file_size,
                    original_path=storage_path,
                    quarantine_path=quarantine_info["quarantine_path"],
                    file_hash=quarantine_info["file_hash"],
                    threat_name=scan_result.threat_name,
                    scanner_output=scan_result.scanner_output,
                    severity="CRITICAL",
                )

                # 3c. 通知业务服务
                await NotificationService.notify_security_event(
                    file_id=file_id,
                    user_id=user_id,
                    threat_name=scan_result.threat_name,
                    action="quarantined",
                    details=f"文件已隔离至 {quarantine_info['quarantine_path']}",
                )

                return VirusScanResult(
                    success=False,
                    infected=True,
                    threat_name=scan_result.threat_name,
                    quarantine_path=quarantine_info["quarantine_path"],
                    file_hash=quarantine_info["file_hash"],
                    failure_reason=FailureReason.VIRUS_FOUND,
                    error=f"文件包含病毒/木马: {scan_result.threat_name}",
                )

            except Exception as e:
                logger.error(f"隔离文件失败: {e}")
                return VirusScanResult(
                    success=False,
                    infected=True,
                    threat_name=scan_result.threat_name,
                    failure_reason=FailureReason.VIRUS_FOUND,
                    error=f"病毒已发现但隔离失败: {e}",
                )

        # 情况 4: 扫描器异常
        return VirusScanResult(
            success=False,
            failure_reason=scan_result.failure_reason or FailureReason.VIRUS_SCANNER_ERROR,
            error=scan_result.scanner_output,
        )