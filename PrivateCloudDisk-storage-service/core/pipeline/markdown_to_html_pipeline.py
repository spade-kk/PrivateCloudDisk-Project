"""
Markdown 文件转 HTML 流水线

将 Markdown 文件转换为 HTML 格式，生成统一的预览资源。
技术方案:
  - 使用 Python-Markdown 库进行 Markdown → HTML 转换
  - 使用 Pygments 进行代码语法高亮（生成内联 CSS）
  - 支持表格、代码块、目录、脚注等扩展语法
  - 转换后的 HTML 文件存储在 previews/ 目录下

与 OfficeToPdfPipeline 一致的设计模式:
  - 静态方法 execute() 作为入口
  - 返回统一的数据类结果
  - 异步执行，不阻塞事件循环

Markdown 扩展功能:
  - fenced_code: 围栏代码块 (```)
  - codehilite: Pygments 代码语法高亮
  - tables: 表格支持
  - toc: 自动生成目录
  - footnotes: 脚注
  - attr_list: 属性列表
  - def_list: 定义列表
  - abbr: 缩写
  - sane_lists: 更合理的列表行为
  - smarty: 智能引号
  - meta: 元数据
"""
from __future__ import annotations
import logging
import os
import asyncio
from dataclasses import dataclass

from core.config import settings, FailureReason, MARKDOWN_TYPES

logger = logging.getLogger("markdown_to_html_pipeline")


@dataclass
class MarkdownToHtmlResult:
    """Markdown 转 HTML 处理结果"""
    success: bool
    skipped: bool = False
    skipped_reason: str = ""
    html_path: str = ""             # 生成的 HTML 文件路径
    html_size: int = 0              # HTML 文件大小 (字节)
    html_content: str = ""          # HTML 内容（用于 API 直接返回）
    toc: str = ""                   # 目录 HTML
    metadata: dict = None           # Markdown 元数据
    error: str = ""
    failure_reason: str = ""

    def __post_init__(self):
        if self.metadata is None:
            self.metadata = {}


class MarkdownToHtmlPipeline:
    """
    Markdown 文件转 HTML 流水线

    处理流程:
      1. 检查文件类型 → 非 Markdown 则跳过
      2. 检查文件是否存在
      3. 读取 Markdown 文件内容
      4. 使用 Python-Markdown 转换为 HTML（含代码高亮）
      5. 包装为完整的 HTML 文档（含 CSS 样式）
      6. 保存到 previews/ 目录
      7. 返回 HTML 路径和内容
    """

    # 输出 HTML 模板（内联 Pygments 代码高亮样式）
    HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
/* ============================================================
   Markdown 预览基础样式
   ============================================================ */
body {{
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
  font-size: 16px;
  line-height: 1.7;
  color: #d4d4d4;
  background: #1e1e1e;
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
}}
h1, h2, h3, h4, h5, h6 {{
  color: #e0e0e0;
  margin-top: 1.5em;
  margin-bottom: 0.5em;
  font-weight: 600;
  line-height: 1.3;
  border-bottom: 1px solid #3e3e42;
  padding-bottom: 0.3em;
}}
h1 {{ font-size: 2em; }}
h2 {{ font-size: 1.5em; }}
h3 {{ font-size: 1.25em; }}
h4 {{ font-size: 1em; }}
a {{ color: #4ea1f3; text-decoration: none; }}
a:hover {{ text-decoration: underline; }}
code {{
  font-family: "Fira Code", "Consolas", "Monaco", monospace;
  font-size: 0.9em;
  background: #2d2d2d;
  padding: 0.2em 0.4em;
  border-radius: 3px;
  color: #ce9178;
}}
pre {{
  background: #252526;
  border: 1px solid #3e3e42;
  border-radius: 6px;
  padding: 1em;
  overflow-x: auto;
  line-height: 1.5;
}}
pre code {{
  background: transparent;
  padding: 0;
  color: #d4d4d4;
}}
blockquote {{
  border-left: 4px solid #007acc;
  margin: 0;
  padding: 0.5em 1em;
  background: #252526;
  color: #9cdcfe;
}}
table {{
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
}}
th, td {{
  border: 1px solid #3e3e42;
  padding: 0.5em 0.75em;
  text-align: left;
}}
th {{
  background: #2d2d2d;
  font-weight: 600;
  color: #e0e0e0;
}}
tr:nth-child(even) {{ background: #252526; }}
img {{ max-width: 100%; height: auto; border-radius: 4px; }}
hr {{ border: none; border-top: 1px solid #3e3e42; margin: 2em 0; }}
ul, ol {{ padding-left: 2em; }}
li {{ margin: 0.25em 0; }}
p {{ margin: 0.75em 0; }}
/* KaTeX 数学公式样式 */
.katex-display {{ overflow-x: auto; overflow-y: hidden; }}
.katex {{ font-size: 1.1em; }}
/* Mermaid 图表样式 */
.mermaid {{ text-align: center; margin: 1em 0; }}
{codehilite_css}
</style>
</head>
<body>
{content}
</body>
</html>"""

    @staticmethod
    async def execute(
        file_id: str,
        storage_path: str,
        file_type: str,
    ) -> MarkdownToHtmlResult:
        """
        执行 Markdown 文件转 HTML 处理

        Args:
            file_id: 文件 ID
            storage_path: 源文件存储路径
            file_type: MIME 类型

        Returns:
            MarkdownToHtmlResult: 处理结果
        """
        logger.info(
            f"开始 Markdown 转 HTML 处理: file_id={file_id}, "
            f"file_type={file_type}, path={storage_path}"
        )

        # 1. 检查文件类型
        if file_type not in MARKDOWN_TYPES:
            logger.info(
                f"非 Markdown 文件，跳过处理: file_id={file_id}, "
                f"type={file_type}"
            )
            return MarkdownToHtmlResult(
                success=True,
                skipped=True,
                skipped_reason="非 Markdown 文件",
            )

        # 2. 检查文件是否存在
        if not os.path.exists(storage_path):
            return MarkdownToHtmlResult(
                success=False,
                failure_reason=FailureReason.MARKDOWN_TO_HTML_ERROR,
                error=f"文件不存在: {storage_path}",
            )

        # 3. 创建输出目录
        preview_dir = os.path.join(settings.file_upload_dir, "previews")
        os.makedirs(preview_dir, exist_ok=True)

        try:
            # 4. 读取 Markdown 文件内容
            loop = asyncio.get_event_loop()
            md_content = await loop.run_in_executor(
                None, MarkdownToHtmlPipeline._read_file, storage_path,
            )

            if md_content is None:
                return MarkdownToHtmlResult(
                    success=False,
                    failure_reason=FailureReason.MARKDOWN_TO_HTML_ERROR,
                    error="文件读取失败（可能是编码问题）",
                )

            # 5. 转换为 HTML
            html_body, toc_html, metadata = await loop.run_in_executor(
                None, MarkdownToHtmlPipeline._convert_markdown, md_content,
            )

            # 6. 获取 Pygments 代码高亮 CSS
            codehilite_css = await loop.run_in_executor(
                None, MarkdownToHtmlPipeline._get_pygments_css,
            )

            # 7. 包装为完整的 HTML 文档
            full_html = MarkdownToHtmlPipeline.HTML_TEMPLATE.format(
                content=html_body,
                codehilite_css=codehilite_css,
            )

            # 8. 保存 HTML 文件
            html_path = os.path.join(preview_dir, f"{file_id}_md.html")
            await loop.run_in_executor(
                None, MarkdownToHtmlPipeline._write_file, html_path, full_html,
            )

            html_size = os.path.getsize(html_path)

            logger.info(
                f"Markdown 转 HTML 完成: file_id={file_id}, "
                f"html_size={html_size} bytes, "
                f"html_path={html_path}"
            )

            return MarkdownToHtmlResult(
                success=True,
                html_path=html_path,
                html_size=html_size,
                html_content=full_html,
                toc=toc_html,
                metadata=metadata,
            )

        except Exception as e:
            logger.error(f"Markdown 转 HTML 异常: file_id={file_id}, error={e}")
            return MarkdownToHtmlResult(
                success=False,
                failure_reason=FailureReason.MARKDOWN_TO_HTML_ERROR,
                error=str(e),
            )

    @staticmethod
    def _read_file(file_path: str) -> str | None:
        """
        读取文件内容，尝试多种编码

        Args:
            file_path: 文件路径

        Returns:
            str | None: 文件内容，失败返回 None
        """
        encodings = ['utf-8', 'gbk', 'gb2312', 'latin-1']
        for enc in encodings:
            try:
                with open(file_path, 'r', encoding=enc) as f:
                    return f.read()
            except UnicodeDecodeError:
                continue
        return None

    @staticmethod
    def _write_file(file_path: str, content: str) -> None:
        """写入文件"""
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)

    @staticmethod
    def _convert_markdown(md_content: str) -> tuple[str, str, dict]:
        """
        使用 Python-Markdown 将 Markdown 转换为 HTML

        扩展说明:
          - fenced_code: 支持 ``` 围栏代码块
          - codehilite: 使用 Pygments 进行代码语法高亮
          - tables: 支持 GFM 表格
          - toc: 自动生成目录（[TOC] 标记）
          - footnotes: 支持脚注 [^1]
          - attr_list: 支持属性列表 {: .class #id }
          - def_list: 支持定义列表
          - abbr: 支持缩写
          - sane_lists: 更合理的列表行为
          - smarty: 智能引号转换
          - meta: 支持 YAML 元数据头

        Args:
            md_content: Markdown 原始内容

        Returns:
            tuple[str, str, dict]: (HTML 正文, 目录 HTML, 元数据字典)
        """
        try:
            from markdown import Markdown
        except ImportError:
            logger.warning(
                "Python-Markdown 未安装，使用基础转换。"
                "请执行: pip install markdown Pygments"
            )
            return MarkdownToHtmlPipeline._fallback_convert(md_content)

        md = Markdown(
            extensions=[
                'fenced_code',
                'codehilite',
                'tables',
                'toc',
                'footnotes',
                'attr_list',
                'def_list',
                'abbr',
                'sane_lists',
                'smarty',
                'meta',
            ],
            extension_configs={
                'codehilite': {
                    'css_class': 'highlight',
                    'guess_lang': True,
                    'linenums': False,
                    'use_pygments': True,
                    'noclasses': True,  # 使用内联样式
                },
                'toc': {
                    'permalink': True,
                    'permalink_class': 'header-anchor',
                    'baselevel': 2,
                },
            },
        )

        html_body = md.convert(md_content)
        toc_html = getattr(md, 'toc', '') or ''
        metadata = getattr(md, 'Meta', {}) or {}

        # 将元数据值从列表展平
        flat_metadata = {}
        for k, v in metadata.items():
            flat_metadata[k] = v[0] if isinstance(v, list) and len(v) == 1 else v

        return html_body, toc_html, flat_metadata

    @staticmethod
    def _fallback_convert(md_content: str) -> tuple[str, str, dict]:
        """
        降级转换：无 Python-Markdown 时的基础 HTML 转换

        仅处理最基础的 Markdown 语法：
          - 代码块 (```)
          - 标题 (#)
          - 加粗/斜体
          - 链接
          - 无序列表

        Args:
            md_content: Markdown 原始内容

        Returns:
            tuple[str, str, dict]: (HTML 正文, 空目录, 空元数据)
        """
        import re

        html = md_content

        # 1. 代码块处理 (```language\ncode\n```)
        def replace_code_block(m):
            lang = m.group(1) or ''
            code = m.group(2)
            # 基础 HTML 转义
            code = code.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
            lang_class = f' class="language-{lang}"' if lang else ''
            return f'<pre><code{lang_class}>{code}</code></pre>'

        html = re.sub(r'```(\w*)\n(.*?)```', replace_code_block, html, flags=re.DOTALL)

        # 2. 行内代码
        html = re.sub(r'`([^`]+)`', r'<code>\1</code>', html)

        # 3. 标题
        html = re.sub(r'^###### (.+)$', r'<h6>\1</h6>', html, flags=re.MULTILINE)
        html = re.sub(r'^##### (.+)$', r'<h5>\1</h5>', html, flags=re.MULTILINE)
        html = re.sub(r'^#### (.+)$', r'<h4>\1</h4>', html, flags=re.MULTILINE)
        html = re.sub(r'^### (.+)$', r'<h3>\1</h3>', html, flags=re.MULTILINE)
        html = re.sub(r'^## (.+)$', r'<h2>\1</h2>', html, flags=re.MULTILINE)
        html = re.sub(r'^# (.+)$', r'<h1>\1</h1>', html, flags=re.MULTILINE)

        # 4. 加粗和斜体
        html = re.sub(r'\*\*\*(.+?)\*\*\*', r'<strong><em>\1</em></strong>', html)
        html = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', html)
        html = re.sub(r'\*(.+?)\*', r'<em>\1</em>', html)

        # 5. 链接
        html = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', r'<a href="\2">\1</a>', html)

        # 6. 图片
        html = re.sub(r'!\[([^\]]*)\]\(([^)]+)\)', r'<img src="\2" alt="\1">', html)

        # 7. 无序列表
        html = re.sub(r'^- (.+)$', r'<li>\1</li>', html, flags=re.MULTILINE)
        html = re.sub(r'(<li>.*</li>\n?)+', r'<ul>\g<0></ul>', html)

        # 8. 段落（连续非空行）
        html = re.sub(r'\n\n+', '</p><p>', html)
        html = f'<p>{html}</p>'

        return html, '', {}

    @staticmethod
    def _get_pygments_css() -> str:
        """
        获取 Pygments 代码高亮 CSS（VS Code Dark+ 风格）

        Returns:
            str: CSS 样式字符串
        """
        try:
            from pygments.formatters import HtmlFormatter
            # 使用 monokai 风格（接近 VS Code Dark）
            return HtmlFormatter(style='monokai').get_style_defs('.highlight')
        except ImportError:
            # Pygments 未安装，返回基础样式
            return """
.highlight { background: #252526; border-radius: 6px; padding: 1em; overflow-x: auto; }
.highlight pre { margin: 0; }
.highlight .hll { background-color: #49483e; }
.highlight .c { color: #75715e; } /* Comment */
.highlight .k { color: #66d9ef; } /* Keyword */
.highlight .s { color: #e6db74; } /* String */
.highlight .n { color: #f8f8f2; } /* Name */
.highlight .p { color: #f8f8f2; } /* Punctuation */
.highlight .o { color: #f92672; } /* Operator */
.highlight .mi { color: #ae81ff; } /* Number */
.highlight .nb { color: #66d9ef; } /* Builtin */
.highlight .nc { color: #a6e22e; } /* Class */
.highlight .nf { color: #a6e22e; } /* Function */
.highlight .nd { color: #a6e22e; } /* Decorator */
"""