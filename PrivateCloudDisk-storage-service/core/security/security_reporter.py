"""
安全报告服务
生成详细的安全事件报告日志，记录病毒/木马检测的完整上下文
"""
from __future__ import annotations
import logging
import json
import os
from datetime import datetime, timezone
from typing import Optional
from core.config import settings

logger = logging.getLogger("security_reporter")


class SecurityReporter:
    """
    安全事件报告器

    生成结构化安全报告，包含:
    1. 事件摘要
    2. 文件信息
    3. 用户信息
    4. 威胁详情
    5. 处置措施
    6. 建议后续操作
    """

    def __init__(self, report_dir: str | None = None):
        self.report_dir = report_dir or os.path.join(settings.quarantine_dir, "reports")
        os.makedirs(self.report_dir, exist_ok=True)

    async def generate_threat_report(
        self,
        event_id: str,
        file_id: str,
        user_id: str,
        file_name: str,
        file_type: str,
        file_size: int,
        original_path: str,
        quarantine_path: str,
        file_hash: str,
        threat_name: str,
        scanner_output: str,
        severity: str = "HIGH",
    ) -> str:
        """
        生成威胁报告

        Returns:
            str: 报告文件路径
        """
        timestamp = datetime.now(timezone.utc)
        report = {
            "report_id": f"SEC-{timestamp.strftime('%Y%m%d%H%M%S')}-{event_id[:8]}",
            "event_id": event_id,
            "severity": severity,
            "timestamp": timestamp.isoformat(),
            "event_type": "VIRUS_DETECTED",
            "threat": {
                "name": threat_name,
                "scanner": "ClamAV",
                "raw_output": scanner_output,
            },
            "file": {
                "file_id": file_id,
                "file_name": file_name,
                "file_type": file_type,
                "file_size": file_size,
                "file_hash": file_hash,
                "original_path": original_path,
                "quarantine_path": quarantine_path,
            },
            "actor": {
                "user_id": user_id,
            },
            "action": {
                "type": "QUARANTINED",
                "description": "文件已移动到隔离区，权限设为只读 (0o400)，禁止执行",
                "quarantine_path": quarantine_path,
            },
            "recommendations": [
                "1. 确认该用户上传行为是否正常",
                "2. 检查该用户是否近期上传了其他可疑文件",
                "3. 如确认为恶意文件，建议封禁上传者账号",
                "4. 将文件哈希加入黑名单，防止重新上传",
                "5. 如为误报，可从隔离区释放文件",
            ],
            "status": "OPEN",
        }

        # 写入 JSON 报告
        report_filename = f"{report['report_id']}.json"
        report_path = os.path.join(self.report_dir, report_filename)
        with open(report_path, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2, ensure_ascii=False)

        # 同时写入人类可读的文本报告
        txt_report = self._generate_text_report(report)
        txt_path = report_path.replace(".json", ".txt")
        with open(txt_path, "w", encoding="utf-8") as f:
            f.write(txt_report)

        logger.critical(
            f"\n{'='*60}\n"
            f"  ⚠ 安全威胁报告\n"
            f"{'='*60}\n"
            f"  报告编号: {report['report_id']}\n"
            f"  严重级别: {severity}\n"
            f"  事件类型: 病毒/木马检测\n"
            f"  威胁名称: {threat_name}\n"
            f"  文件名称: {file_name}\n"
            f"  文件ID:   {file_id}\n"
            f"  用户ID:   {user_id}\n"
            f"  文件哈希: {file_hash}\n"
            f"  原始路径: {original_path}\n"
            f"  隔离路径: {quarantine_path}\n"
            f"  处置措施: 已隔离 (只读, 禁止执行)\n"
            f"  报告文件: {report_path}\n"
            f"{'='*60}"
        )

        return report_path

    @staticmethod
    def _generate_text_report(report: dict) -> str:
        """生成人类可读的文本报告"""
        lines = [
            "=" * 60,
            "  私有云盘系统 - 安全威胁报告",
            "=" * 60,
            "",
            f"报告编号: {report['report_id']}",
            f"严重级别: {report['severity']}",
            f"时间:     {report['timestamp']}",
            f"事件类型: {report['event_type']}",
            "",
            "-" * 40,
            " 威胁信息",
            "-" * 40,
            f"威胁名称: {report['threat']['name']}",
            f"扫描引擎: {report['threat']['scanner']}",
            f"扫描输出: {report['threat']['raw_output']}",
            "",
            "-" * 40,
            " 文件信息",
            "-" * 40,
            f"文件ID:   {report['file']['file_id']}",
            f"文件名:   {report['file']['file_name']}",
            f"文件类型: {report['file']['file_type']}",
            f"文件大小: {report['file']['file_size']} bytes",
            f"文件哈希: {report['file']['file_hash']}",
            f"原始路径: {report['file']['original_path']}",
            f"隔离路径: {report['file']['quarantine_path']}",
            "",
            "-" * 40,
            " 用户信息",
            "-" * 40,
            f"用户ID:   {report['actor']['user_id']}",
            "",
            "-" * 40,
            " 处置措施",
            "-" * 40,
            f"处置类型: {report['action']['type']}",
            f"描述:     {report['action']['description']}",
            f"隔离路径: {report['action']['quarantine_path']}",
            "",
            "-" * 40,
            " 建议后续操作",
            "-" * 40,
        ]
        for rec in report["recommendations"]:
            lines.append(rec)
        lines.append("")
        lines.append("=" * 60)
        return "\n".join(lines)


# 全局单例
security_reporter = SecurityReporter()