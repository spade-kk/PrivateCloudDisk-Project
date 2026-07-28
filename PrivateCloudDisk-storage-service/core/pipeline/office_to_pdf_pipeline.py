"""
Office 文件转 PDF 流水线

将 Office 文档（Word/Excel/PPT）转换为 PDF 格式，生成统一的预览资源。
PDF 文件本身则跳过转换，但生成缩略图等预览资源。

技术方案:
  - 使用 LibreOffice 无头模式进行 Office → PDF 转换
  - 使用 PyMuPDF (fitz) 为 PDF 生成首页缩略图
  - 转换后的 PDF 文件存储在 previews/ 目录下

与 TranscodePipeline 一致的设计模式:
  - 静态方法 execute() 作为入口
  - 返回统一的数据类结果
  - 异步执行，不阻塞事件循环
"""
from __future__ import annotations
import logging
import os
import asyncio
import shutil
import subprocess
from dataclasses import dataclass, field
from core.config import settings, FailureReason, OFFICE_TYPES, PDF_TYPES

logger = logging.getLogger("office_to_pdf_pipeline")


@dataclass
class OfficeToPdfResult:
    """Office 转 PDF 处理结果"""
    success: bool
    skipped: bool = False
    skipped_reason: str = ""
    pdf_path: str = ""                           # 生成的 PDF 文件路径
    pdf_size: int = 0                            # PDF 文件大小 (字节)
    preview_path: str = ""                       # PDF 首页缩略图路径
    preview_paths: dict = field(default_factory=dict)  # original/large/medium/small 四档文档封面
    preview_metadata: dict = field(default_factory=dict)  # 各档宽高与文件大小
    page_count: int = 0                          # 转换后文档页数
    source_type: str = ""                        # 源文件类型 (office / pdf)
    error: str = ""
    failure_reason: str = ""


# LibreOffice 路径配置（按优先级探测）
LIBREOFFICE_PATHS = [
    "/usr/bin/libreoffice",
    "/usr/local/bin/libreoffice",
    "/opt/libreoffice/program/soffice",
    "libreoffice",
    "soffice",
]

# PDF 转图片的默认 DPI
# AUDIT FIX [5.2]（需求五-2/4）：原 150 DPI 只适合列表缩略图；原图档提高到 220 DPI，
# 再从同一高质量渲染结果派生其余规格，避免多次 PDF 光栅化产生视觉偏差。
PDF_PREVIEW_DPI = 220

# 文档封面图规格采用“边界盒”而非强制裁剪，完整保留 Word/PDF/Excel/PPT 首页比例。
DOCUMENT_PREVIEW_VARIANTS = {
    "large": (1600, 2000, 92),
    "medium": (960, 1200, 88),
    "small": (360, 450, 82),
}

# 转换超时时间（秒）
CONVERSION_TIMEOUT = 300


class OfficeToPdfPipeline:
    """
    Office 文件转 PDF 流水线

    处理流程:
      1. 检查文件类型 → 非 Office/PDF 则跳过
      2. 检查文件是否存在
      3. Office 文件 → 使用 LibreOffice 转换为 PDF
      4. PDF 文件 → 跳过转换，直接使用原文件
      5. 生成 PDF 首页缩略图
      6. 返回 PDF 路径和缩略图路径
    """

    @staticmethod
    async def execute(
        file_id: str,
        storage_path: str,
        file_type: str,
    ) -> OfficeToPdfResult:
        """
        执行 Office 文件转 PDF 处理

        Args:
            file_id: 文件 ID
            storage_path: 源文件存储路径
            file_type: MIME 类型

        Returns:
            OfficeToPdfResult: 处理结果
        """
        logger.info(
            f"开始 Office 转 PDF 处理: file_id={file_id}, "
            f"file_type={file_type}, path={storage_path}"
        )

        # 判断源文件类型
        if file_type in OFFICE_TYPES:
            source_type = "office"
        elif file_type in PDF_TYPES:
            source_type = "pdf"
        else:
            logger.info(
                f"非 Office/PDF 文件，跳过处理: file_id={file_id}, "
                f"type={file_type}"
            )
            return OfficeToPdfResult(
                success=True,
                skipped=True,
                skipped_reason="非 Office/PDF 文件",
                source_type="unknown",
            )

        # 检查文件是否存在
        if not os.path.exists(storage_path):
            return OfficeToPdfResult(
                success=False,
                failure_reason=FailureReason.OFFICE_TO_PDF_ERROR,
                error=f"文件不存在: {storage_path}",
                source_type=source_type,
            )

        # 创建输出目录
        preview_dir = os.path.join(settings.file_upload_dir, "previews")
        thumbnail_dir = os.path.join(settings.file_upload_dir, "thumbnails")
        os.makedirs(preview_dir, exist_ok=True)
        os.makedirs(thumbnail_dir, exist_ok=True)

        try:
            pdf_path = ""
            pdf_size = 0

            if source_type == "office":
                # Office 文件 → 使用 LibreOffice 转换为 PDF
                pdf_path = os.path.join(preview_dir, f"{file_id}.pdf")

                conversion_success = await OfficeToPdfPipeline._convert_to_pdf(
                    storage_path, pdf_path,
                )
                if not conversion_success:
                    return OfficeToPdfResult(
                        success=False,
                        failure_reason=FailureReason.OFFICE_TO_PDF_ERROR,
                        error="LibreOffice 转换失败",
                        source_type=source_type,
                    )

                pdf_size = os.path.getsize(pdf_path)
                logger.info(
                    f"Office 转 PDF 完成: file_id={file_id}, "
                    f"pdf_size={pdf_size} bytes"
                )

            elif source_type == "pdf":
                # PDF 文件 → 直接使用原文件作为预览
                # 复制到预览目录以便统一管理
                pdf_path = os.path.join(preview_dir, f"{file_id}.pdf")
                try:
                    shutil.copy2(storage_path, pdf_path)
                    pdf_size = os.path.getsize(pdf_path)
                    logger.info(
                        f"PDF 文件已复制到预览目录: file_id={file_id}, "
                        f"pdf_size={pdf_size} bytes"
                    )
                except Exception as e:
                    logger.warning(
                        f"复制 PDF 到预览目录失败，使用原路径: "
                        f"file_id={file_id}, error={e}"
                    )
                    pdf_path = storage_path
                    pdf_size = os.path.getsize(pdf_path)

            # 生成 PDF 首页四档预览图
            preview_paths, preview_metadata, page_count = await OfficeToPdfPipeline._generate_pdf_previews(
                pdf_path, file_id, thumbnail_dir,
            )
            # 保留原 preview_path 字段供尚未升级的调用方兼容读取。
            preview_path = preview_paths.get("medium") or preview_paths.get("original") or ""

            logger.info(
                f"Office/PDF 处理完成: file_id={file_id}, "
                f"source_type={source_type}, "
                f"pdf_path={pdf_path}, "
                f"preview={preview_path or '无'}"
            )

            return OfficeToPdfResult(
                success=True,
                pdf_path=pdf_path,
                pdf_size=pdf_size,
                preview_path=preview_path,
                preview_paths=preview_paths,
                preview_metadata=preview_metadata,
                page_count=page_count,
                source_type=source_type,
            )

        except Exception as e:
            logger.error(f"Office 转 PDF 异常: file_id={file_id}, error={e}")
            return OfficeToPdfResult(
                success=False,
                failure_reason=FailureReason.OFFICE_TO_PDF_ERROR,
                error=str(e),
                source_type=source_type,
            )

    @staticmethod
    async def _find_libreoffice() -> str | None:
        """
        探测 LibreOffice 可执行文件路径

        按优先级检测多个路径，返回第一个可用的。

        Returns:
            str | None: LibreOffice 路径，未找到返回 None
        """
        for path in LIBREOFFICE_PATHS:
            # 检查绝对路径是否存在
            if os.path.isabs(path) and os.path.isfile(path) and os.access(path, os.X_OK):
                logger.info(f"找到 LibreOffice: {path}")
                return path
            # 检查 PATH 中是否有该命令
            if not os.path.isabs(path):
                found = shutil.which(path)
                if found:
                    logger.info(f"找到 LibreOffice (PATH): {found}")
                    return found

        logger.warning("未找到 LibreOffice，Office 文件转 PDF 将不可用")
        return None

    @staticmethod
    async def _convert_to_pdf(input_path: str, output_path: str) -> bool:
        """
        使用 LibreOffice 无头模式将 Office 文件转换为 PDF

        Args:
            input_path: 源文件路径
            output_path: 目标 PDF 路径

        Returns:
            bool: 转换是否成功
        """
        libreoffice_path = await OfficeToPdfPipeline._find_libreoffice()
        if not libreoffice_path:
            logger.error("LibreOffice 未安装，无法转换 Office 文件")
            return False

        output_dir = os.path.dirname(output_path)
        os.makedirs(output_dir, exist_ok=True)

        # LibreOffice 命令行参数说明:
        #   --headless          无头模式，不启动 GUI
        #   --convert-to pdf    转换为 PDF 格式
        #   --outdir            输出目录（LibreOffice 使用原始文件名）
        #   注意: LibreOffice 自动命名输出文件为 {basename}.pdf
        input_basename = os.path.basename(input_path)
        input_name_without_ext = os.path.splitext(input_basename)[0]

        cmd = [
            libreoffice_path,
            "--headless",
            "--norestore",
            "--invisible",
            "--convert-to", "pdf",
            "--outdir", output_dir,
            input_path,
        ]

        try:
            logger.info(
                f"LibreOffice 转换开始: input={input_path}, "
                f"output_dir={output_dir}"
            )

            process = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )

            stdout, stderr = await asyncio.wait_for(
                process.communicate(),
                timeout=CONVERSION_TIMEOUT,
            )

            if process.returncode != 0:
                stderr_text = stderr.decode("utf-8", errors="replace")[:500]
                logger.error(
                    f"LibreOffice 转换失败: returncode={process.returncode}, "
                    f"stderr={stderr_text}"
                )
                return False

            # LibreOffice 生成的 PDF 文件名是 {basename}.pdf
            expected_pdf = os.path.join(
                output_dir, f"{input_name_without_ext}.pdf"
            )

            if os.path.exists(expected_pdf):
                # 如果期望路径与目标路径不同，重命名
                if expected_pdf != output_path:
                    os.rename(expected_pdf, output_path)
                logger.info(
                    f"LibreOffice 转换成功: output={output_path}, "
                    f"size={os.path.getsize(output_path)} bytes"
                )
                return True

            # 回退: 检查目录下是否有新生成的 PDF 文件
            for fname in os.listdir(output_dir):
                if fname.endswith(".pdf") and fname != os.path.basename(output_path):
                    candidate = os.path.join(output_dir, fname)
                    if os.path.getsize(candidate) > 0:
                        os.rename(candidate, output_path)
                        logger.info(
                            f"LibreOffice 转换成功 (回退匹配): "
                            f"output={output_path}"
                        )
                        return True

            logger.error("LibreOffice 转换后未找到输出 PDF 文件")
            return False

        except asyncio.TimeoutError:
            logger.error(
                f"LibreOffice 转换超时 ({CONVERSION_TIMEOUT}s): "
                f"input={input_path}"
            )
            # AUDIT FIX [7.5]（需求一-3）：原行为超时后仅返回失败，子进程仍可能持续占用 CPU/内存。
            # 新行为先温和终止，5 秒内未退出则强制 kill，并等待回收进程句柄。
            if process.returncode is None:
                process.terminate()
                try:
                    await asyncio.wait_for(process.wait(), timeout=5)
                except asyncio.TimeoutError:
                    process.kill()
                    await process.wait()
            return False
        except Exception as e:
            logger.error(f"LibreOffice 转换异常: {e}")
            return False

    @staticmethod
    async def _generate_pdf_previews(
        pdf_path: str, file_id: str, output_dir: str,
    ) -> tuple[dict[str, str], dict[str, dict], int]:
        """
        使用 PyMuPDF 生成 PDF 首页四档预览图

        AUDIT FIX [5.2]（需求五）：
        原行为只输出单张 150 DPI JPEG，无法同时满足网格、悬停和全屏质量；
        新行为一次高质量渲染后生成 original/large/medium/small 四档 JPEG，
        全部保持首页原始宽高比，不裁剪、不拉伸。

        Args:
            pdf_path: PDF 文件路径
            file_id: 文件 ID
            output_dir: 缩略图输出目录

        Returns:
            tuple: 预览路径、各档元数据、文档页数
        """
        os.makedirs(output_dir, exist_ok=True)

        try:
            # 尝试导入 PyMuPDF
            import fitz  # PyMuPDF

            result = await asyncio.to_thread(
                OfficeToPdfPipeline._render_pdf_page_variants,
                pdf_path, file_id, output_dir,
            )
            preview_paths, preview_metadata, page_count = result
            if preview_paths:
                logger.info(
                    f"PDF 首页四档预览图生成成功: file_id={file_id}, "
                    f"variants={list(preview_paths.keys())}"
                )
                return preview_paths, preview_metadata, page_count

            logger.warning(f"PDF 首页缩略图生成后文件为空: file_id={file_id}")
            return {}, {}, page_count

        except ImportError:
            logger.warning(
                "PyMuPDF (fitz) 未安装，跳过 PDF 缩略图生成。"
                "安装: pip install PyMuPDF"
            )
            return {}, {}, 0
        except Exception as e:
            logger.warning(
                f"PDF 首页缩略图生成失败: file_id={file_id}, error={e}"
            )
            return {}, {}, 0

    @staticmethod
    def _render_pdf_page_variants(
        pdf_path: str, file_id: str, output_dir: str,
    ) -> tuple[dict[str, str], dict[str, dict], int]:
        """
        渲染 PDF 第一页并派生四档 JPEG（同步方法，在线程池中执行）

        使用 PyMuPDF 保证 Office 转换后的版式一致性，再用 Pillow 的 LANCZOS
        做高质量等比缩放。所有文件在临时路径写完后原子替换，避免接口读到半张图片。

        Args:
            pdf_path: PDF 源文件路径
            file_id: 文件 ID
            output_dir: 输出目录
        """
        import fitz  # PyMuPDF
        from PIL import Image

        doc = fitz.open(pdf_path)
        try:
            if len(doc) == 0:
                logger.warning(f"PDF 文件无页面: {pdf_path}")
                return {}, {}, 0

            # 渲染第一页
            page = doc[0]

            # 计算缩放矩阵以达到目标 DPI
            zoom = PDF_PREVIEW_DPI / 72.0  # PDF 默认 72 DPI
            mat = fitz.Matrix(zoom, zoom)

            pix = page.get_pixmap(matrix=mat, alpha=False)
            image = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)

            paths: dict[str, str] = {}
            metadata: dict[str, dict] = {}
            variants = {"original": None, **DOCUMENT_PREVIEW_VARIANTS}
            for variant, config in variants.items():
                rendered = image.copy()
                quality = 97
                if config:
                    max_width, max_height, quality = config
                    rendered.thumbnail((max_width, max_height), Image.Resampling.LANCZOS)

                output_path = os.path.join(output_dir, f"{file_id}_office_{variant}.jpg")
                temporary_path = f"{output_path}.tmp"
                rendered.save(
                    temporary_path,
                    format="JPEG",
                    quality=quality,
                    optimize=True,
                    progressive=True,
                    subsampling=0 if variant == "original" else 2,
                )
                os.replace(temporary_path, output_path)
                paths[variant] = output_path
                metadata[variant] = {
                    "width": rendered.width,
                    "height": rendered.height,
                    "size_bytes": os.path.getsize(output_path),
                    "quality": quality,
                }
                rendered.close()

            logger.debug(
                f"PDF 页面渲染完成: "
                f"size={pix.width}x{pix.height}, "
                f"dpi={PDF_PREVIEW_DPI}"
            )
            image.close()
            return paths, metadata, len(doc)
        finally:
            doc.close()
