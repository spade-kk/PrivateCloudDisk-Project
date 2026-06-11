"""
视频转码流水线
使用 ffmpeg 将视频转码为多分辨率 H.264 + AAC 格式
"""
from __future__ import annotations
import logging
import os
import asyncio
from dataclasses import dataclass, field
from core.config import settings, FailureReason, VIDEO_TYPES

logger = logging.getLogger("transcode_pipeline")


@dataclass
class TranscodeResult:
    success: bool
    skipped: bool = False
    skipped_reason: str = ""
    transcoded_files: list = field(default_factory=list)
    error: str = ""
    failure_reason: str = ""


# 转码分辨率配置
TRANSCODE_PRESETS = [
    {"width": 480, "height": 360, "bitrate": "500k", "label": "480p", "crf": "26"},
    {"width": 960, "height": 540, "bitrate": "1500k", "label": "540p", "crf": "23"},
    {"width": 1920, "height": 1080, "bitrate": "4000k", "label": "1080p", "crf": "21"},
]


class TranscodePipeline:
    """视频转码处理流水线"""

    @staticmethod
    async def execute(
        file_id: str,
        storage_path: str,
        file_type: str,
    ) -> TranscodeResult:
        """
        执行视频转码

        流程:
        1. 检查是否为视频类型 → 不是则跳过
        2. 检查 ffmpeg 是否可用
        3. 按分辨率逐个转码
        4. 生成视频预览图
        """
        logger.info(f"开始视频转码: file_id={file_id}")

        if file_type not in VIDEO_TYPES:
            logger.info(f"非视频文件，跳过转码: file_id={file_id}, type={file_type}")
            return TranscodeResult(success=True, skipped=True, skipped_reason="非视频文件")

        if not os.path.exists(storage_path):
            return TranscodeResult(
                success=False,
                failure_reason=FailureReason.TRANSCODE_ERROR,
                error=f"文件不存在: {storage_path}",
            )

        # 检查 ffmpeg
        if not await TranscodePipeline._ffmpeg_available():
            logger.warning("ffmpeg 未安装，跳过视频转码")
            return TranscodeResult(success=True, skipped=True, skipped_reason="ffmpeg 未安装")

        transcode_dir = os.path.join(settings.file_upload_dir, "transcoded")
        thumbnail_dir = os.path.join(settings.file_upload_dir, "thumbnails")
        os.makedirs(transcode_dir, exist_ok=True)
        os.makedirs(thumbnail_dir, exist_ok=True)

        transcoded_files = []

        try:
            # 1. 生成视频预览图
            preview_path = await TranscodePipeline._generate_preview(
                storage_path, file_id, thumbnail_dir,
            )
            if preview_path:
                transcoded_files.append({"type": "preview", "path": preview_path})

            # 2. 按分辨率转码
            for preset in TRANSCODE_PRESETS:
                output_path = os.path.join(transcode_dir, f"{file_id}_{preset['label']}.mp4")

                try:
                    success = await TranscodePipeline._transcode_single(
                        storage_path, output_path, preset,
                    )
                    if success:
                        transcoded_files.append({
                            "resolution": preset["label"],
                            "path": output_path,
                            "bitrate": preset["bitrate"],
                        })
                except asyncio.TimeoutError:
                    logger.warning(f"转码超时: {preset['label']}")
                except Exception as e:
                    logger.warning(f"转码失败 {preset['label']}: {e}")

            if not transcoded_files:
                return TranscodeResult(
                    success=False,
                    failure_reason=FailureReason.TRANSCODE_ERROR,
                    error="所有分辨率转码均失败",
                )

            logger.info(f"视频转码完成: file_id={file_id}, outputs={len(transcoded_files)}")
            return TranscodeResult(success=True, transcoded_files=transcoded_files)

        except Exception as e:
            logger.error(f"视频转码异常: {e}")
            return TranscodeResult(
                success=False,
                failure_reason=FailureReason.TRANSCODE_ERROR,
                error=str(e),
            )

    @staticmethod
    async def _ffmpeg_available() -> bool:
        """检查 ffmpeg 是否可用"""
        try:
            proc = await asyncio.create_subprocess_exec(
                "ffmpeg", "-version",
                stdout=asyncio.subprocess.DEVNULL,
                stderr=asyncio.subprocess.DEVNULL,
            )
            await proc.wait()
            return proc.returncode == 0
        except FileNotFoundError:
            return False

    @staticmethod
    async def _generate_preview(
        input_path: str, file_id: str, output_dir: str,
    ) -> str | None:
        """生成视频预览图 (第 1 秒帧)"""
        preview_path = os.path.join(output_dir, f"{file_id}_preview.jpg")
        try:
            proc = await asyncio.create_subprocess_exec(
                "ffmpeg",
                "-i", input_path,
                "-ss", "00:00:01",
                "-vframes", "1",
                "-q:v", "2",
                "-y",
                preview_path,
                stdout=asyncio.subprocess.DEVNULL,
                stderr=asyncio.subprocess.PIPE,
            )
            _, stderr = await asyncio.wait_for(proc.communicate(), timeout=60)
            if proc.returncode == 0 and os.path.exists(preview_path):
                return preview_path
            else:
                stderr_text = stderr.decode("utf-8", errors="replace") if stderr else ""
                logger.warning(f"预览图生成失败: {stderr_text[:200]}")
                return None
        except asyncio.TimeoutError:
            logger.warning("预览图生成超时")
            return None
        except Exception as e:
            logger.warning(f"预览图生成异常: {e}")
            return None

    @staticmethod
    async def _transcode_single(
        input_path: str, output_path: str, preset: dict,
    ) -> bool:
        """转码单个分辨率"""
        cmd = [
            "ffmpeg",
            "-i", input_path,
            "-s", f"{preset['width']}x{preset['height']}",
            "-b:v", preset["bitrate"],
            "-c:v", "libx264",
            "-preset", "medium",
            "-crf", preset["crf"],
            "-c:a", "aac",
            "-b:a", "128k",
            "-movflags", "+faststart",
            "-y",
            output_path,
        ]

        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.DEVNULL,
            stderr=asyncio.subprocess.PIPE,
        )

        _, stderr = await asyncio.wait_for(proc.communicate(), timeout=600)

        if proc.returncode != 0:
            stderr_text = stderr.decode("utf-8", errors="replace") if stderr else ""
            logger.warning(f"转码失败 {preset['label']}: {stderr_text[:200]}")
            return False

        return True