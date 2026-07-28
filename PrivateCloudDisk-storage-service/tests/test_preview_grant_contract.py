"""Preview Token 白名单与体积边界契约测试。"""
from __future__ import annotations

import unittest

from fastapi import HTTPException

from app.core.preview_grant import classify_preview_file
from core.config import settings


class PreviewGrantContractTests(unittest.TestCase):
    """覆盖代码、Markdown、图片以及明确拒绝的专用预览文件类型。"""

    def test_code_extensions_are_classified_as_text(self):
        for file_name in ("demo.js", "worker.py", "Main.java", "README.txt"):
            with self.subTest(file_name=file_name):
                kind, size_limit, response_mime = classify_preview_file(
                    file_name, "application/octet-stream", 128,
                )
                self.assertEqual(kind, "text")
                self.assertEqual(size_limit, settings.preview_text_max_bytes)
                self.assertTrue(response_mime)

    def test_markdown_and_image_use_distinct_size_limits(self):
        markdown = classify_preview_file("README.md", "text/markdown", 1024)
        image = classify_preview_file("cover.png", "image/png", 1024)
        self.assertEqual(markdown[:2], ("markdown", settings.preview_text_max_bytes))
        self.assertEqual(image[:2], ("image", settings.preview_image_max_bytes))

    def test_active_content_and_office_files_are_rejected(self):
        for file_name in ("attack.svg", "report.docx", "slides.pptx", "video.mp4"):
            with self.subTest(file_name=file_name):
                with self.assertRaises(HTTPException) as context:
                    classify_preview_file(file_name, "application/octet-stream", 128)
                self.assertEqual(context.exception.status_code, 415)

    def test_oversized_text_is_rejected_before_token_issue(self):
        with self.assertRaises(HTTPException) as context:
            classify_preview_file(
                "huge.py",
                "text/x-python",
                settings.preview_text_max_bytes + 1,
            )
        self.assertEqual(context.exception.status_code, 413)


if __name__ == "__main__":
    unittest.main()
