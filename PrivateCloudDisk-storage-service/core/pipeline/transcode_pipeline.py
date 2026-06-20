"""
视频转码流水线
使用 ffmpeg-python 库将视频转码为多分辨率 H.264 + AAC 格式
"""
from __future__ import annotations
import logging
import os
import asyncio
from dataclasses import dataclass, field
import ffmpeg
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
        """检查 ffmpeg 是否可用（使用 ffmpeg-python 探测）"""
        try:
            await asyncio.to_thread(
                ffmpeg.probe, "dummy",
            )
            # ffmpeg.probe 会抛出 Error 如果 ffmpeg 不可用
            return False
        except ffmpeg.Error:
            # ffmpeg 存在但 dummy 不是有效文件，说明 ffmpeg 可用
            return True
        except FileNotFoundError:
            return False

    @staticmethod
    async def _generate_preview(
        input_path: str, file_id: str, output_dir: str,
    ) -> str | None:
        """生成视频预览图 (第 1 秒帧)"""
        preview_path = os.path.join(output_dir, f"{file_id}_preview.jpg")
        try:
            await asyncio.wait_for(
                asyncio.to_thread(
                    lambda: (
                        ffmpeg
                        .input(input_path, ss="00:00:01")
                        .output(preview_path, vframes=1, q="2")
                        .overwrite_output()
                        .run(capture_stdout=True, capture_stderr=True)
                    )
                ),
                timeout=60,
            )
            if os.path.exists(preview_path):
                return preview_path
            return None
        except asyncio.TimeoutError:
            logger.warning("预览图生成超时")
            return None
        except ffmpeg.Error as e:
            logger.warning(f"预览图生成失败: {e.stderr.decode('utf-8', errors='replace')[:200] if e.stderr else 'unknown'}")
            return None
        except Exception as e:
            logger.warning(f"预览图生成异常: {e}")
            return None

    @staticmethod
    async def _transcode_single(
        input_path: str, output_path: str, preset: dict,
    ) -> bool:
        """转码单个分辨率"""
        try:
            await asyncio.wait_for(
                asyncio.to_thread(
                    lambda: (
                        ffmpeg
                        .input(input_path)
                        .output(
                            output_path,
                            vcodec="libx264",
                            preset="medium",
                            crf=preset["crf"],
                            s=f"{preset['width']}x{preset['height']}",
                            **{"b:v": preset["bitrate"]},
                            acodec="aac",
                            **{"b:a": "128k"},
                            movflags="+faststart",
                        )
                        .overwrite_output()
                        .run(capture_stdout=True, capture_stderr=True)
                    )
                ),
                timeout=600,
            )
            return True
        except asyncio.TimeoutError:
            logger.warning(f"转码超时: {preset['label']}")
            return False
        except ffmpeg.Error as e:
            stderr_text = e.stderr.decode("utf-8", errors="replace") if e.stderr else ""
            logger.warning(f"转码失败 {preset['label']}: {stderr_text[:200]}")
            return False
        except Exception as e:
            logger.warning(f"转码异常 {preset['label']}: {e}")
            return False