"""
缩略图生成流水线
使用 libvips 为图片生成多尺寸缩略图
"""
from __future__ import annotations
import logging
import os
import asyncio
from dataclasses import dataclass, field
from core.config import settings, FailureReason, IMAGE_TYPES

logger = logging.getLogger("thumbnail_pipeline")


@dataclass
class ThumbnailResult:
    success: bool
    skipped: bool = False
    skipped_reason: str = ""
    thumbnails: list = field(default_factory=list)
    error: str = ""
    failure_reason: str = ""


# 支持的缩略图尺寸
THUMBNAIL_SIZES = [
    (100, 100, "xs"),
    (200, 200, "sm"),
    (400, 400, "md"),
    (800, 800, "lg"),
]


class ThumbnailPipeline:
    """缩略图生成流水线"""

    @staticmethod
    async def execute(
        file_id: str,
        storage_path: str,
        file_type: str,
    ) -> ThumbnailResult:
        """
        生成缩略图

        处理流程:
        1. 检查是否为图片类型 → 不是则跳过
        2. 检查文件是否存在
        3. 使用 libvips 生成多尺寸缩略图
        4. 返回缩略图路径列表
        """
        logger.info(f"开始生成缩略图: file_id={file_id}")

        # 非图片文件跳过
        if file_type not in IMAGE_TYPES:
            logger.info(f"非图片文件，跳过缩略图: file_id={file_id}, type={file_type}")
            return ThumbnailResult(success=True, skipped=True, skipped_reason="非图片文件")

        if not os.path.exists(storage_path):
            return ThumbnailResult(
                success=False,
                failure_reason=FailureReason.THUMBNAIL_ERROR,
                error=f"文件不存在: {storage_path}",
            )

        try:
            import pyvips

            thumbnail_dir = os.path.join(settings.file_upload_dir, "thumbnails")
            os.makedirs(thumbnail_dir, exist_ok=True)

            thumbnail_paths = []

            for width, height, label in THUMBNAIL_SIZES:
                output_path = os.path.join(thumbnail_dir, f"{file_id}_{label}.jpg")

                try:
                    await asyncio.to_thread(
                        ThumbnailPipeline._generate_single,
                        storage_path, output_path, width, height,
                    )
                    thumbnail_paths.append({
                        "size": f"{width}x{height}",
                        "label": label,
                        "path": output_path,
                    })
                except Exception as e:
                    logger.warning(f"生成缩略图 {label} 失败: {e}")
                    # 单个尺寸失败不阻塞其他尺寸
                    continue

            if not thumbnail_paths:
                return ThumbnailResult(
                    success=False,
                    failure_reason=FailureReason.THUMBNAIL_ERROR,
                    error="所有尺寸缩略图生成均失败",
                )

            logger.info(f"缩略图生成完成: file_id={file_id}, count={len(thumbnail_paths)}")
            return ThumbnailResult(success=True, thumbnails=thumbnail_paths)

        except ImportError:
            logger.warning("pyvips 未安装，跳过缩略图")
            return ThumbnailResult(success=True, skipped=True, skipped_reason="pyvips 未安装")
        except Exception as e:
            logger.error(f"缩略图生成异常: {e}")
            return ThumbnailResult(
                success=False,
                failure_reason=FailureReason.THUMBNAIL_ERROR,
                error=str(e),
            )

    @staticmethod
    def _generate_single(input_path: str, output_path: str, width: int, height: int):
        """生成单个尺寸的缩略图"""
        import pyvips

        image = pyvips.Image.new_from_file(input_path, access='sequential')
        scale = min(width / image.width, height / image.height)
        if scale < 1.0:
            image = image.resize(scale, kernel='lanczos3')
        if image.interpretation != pyvips.Interpretation.SRGB:
            image = image.colourspace(pyvips.Interpretation.SRGB)

        image.jpegsave(
            output_path,
            Q=85,
            optimize_coding=True,
            trellis_quant=True,
            overshoot_deringing=True,
            interlace=False,
        )