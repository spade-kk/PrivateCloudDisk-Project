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
PDF_PREVIEW_DPI = 150

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

            # 生成 PDF 首页缩略图
            preview_path = await OfficeToPdfPipeline._generate_pdf_preview(
                pdf_path, file_id, thumbnail_dir,
            )

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
                preview_path=preview_path or "",
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
            return False
        except Exception as e:
            logger.error(f"LibreOffice 转换异常: {e}")
            return False

    @staticmethod
    async def _generate_pdf_preview(
        pdf_path: str, file_id: str, output_dir: str,
    ) -> str | None:
        """
        使用 PyMuPDF 生成 PDF 首页缩略图

        将 PDF 第一页渲染为 JPEG 图片，作为预览缩略图。

        Args:
            pdf_path: PDF 文件路径
            file_id: 文件 ID
            output_dir: 缩略图输出目录

        Returns:
            str | None: 缩略图路径，失败返回 None
        """
        preview_path = os.path.join(output_dir, f"{file_id}_pdf_preview.jpg")
        os.makedirs(output_dir, exist_ok=True)

        try:
            # 尝试导入 PyMuPDF
            import fitz  # PyMuPDF

            await asyncio.to_thread(
                OfficeToPdfPipeline._render_pdf_page,
                pdf_path, preview_path,
            )

            if os.path.exists(preview_path) and os.path.getsize(preview_path) > 0:
                logger.info(
                    f"PDF 首页缩略图生成成功: file_id={file_id}, "
                    f"path={preview_path}"
                )
                return preview_path

            logger.warning(f"PDF 首页缩略图生成后文件为空: file_id={file_id}")
            return None

        except ImportError:
            logger.warning(
                "PyMuPDF (fitz) 未安装，跳过 PDF 缩略图生成。"
                "安装: pip install PyMuPDF"
            )
            return None
        except Exception as e:
            logger.warning(
                f"PDF 首页缩略图生成失败: file_id={file_id}, error={e}"
            )
            return None

    @staticmethod
    def _render_pdf_page(pdf_path: str, output_path: str):
        """
        渲染 PDF 第一页为 JPEG 图片（同步方法，在线程池中执行）

        使用 PyMuPDF 将 PDF 第一页渲染为指定 DPI 的 JPEG 图片。

        Args:
            pdf_path: PDF 源文件路径
            output_path: 输出 JPEG 图片路径
        """
        import fitz  # PyMuPDF

        doc = fitz.open(pdf_path)
        try:
            if len(doc) == 0:
                logger.warning(f"PDF 文件无页面: {pdf_path}")
                return

            # 渲染第一页
            page = doc[0]

            # 计算缩放矩阵以达到目标 DPI
            zoom = PDF_PREVIEW_DPI / 72.0  # PDF 默认 72 DPI
            mat = fitz.Matrix(zoom, zoom)

            pix = page.get_pixmap(matrix=mat)
            pix.save(output_path)

            logger.debug(
                f"PDF 页面渲染完成: "
                f"size={pix.width}x{pix.height}, "
                f"dpi={PDF_PREVIEW_DPI}"
            )
        finally:
            doc.close()