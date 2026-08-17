"""pycloud 生命周期权限与兼容入口测试。"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import pycloud
from pycloud import file


class PyCloudFileTests(unittest.TestCase):
    def test_preprocess_uses_staging_permissions_and_writes_candidate(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "input.bin"
            output = Path(directory) / "output.bin"
            source.write_bytes(b"source")
            pycloud.configure({
                "permissions": [
                    "file.content.read_staging",
                    "file.content.write_pre_activation",
                ],
                "content_frozen": False,
            })
            with patch.object(file, "_INPUT", str(source)), patch.object(
                file, "_OUTPUT", str(output)
            ):
                self.assertEqual(file.read_staging("logical-file-id"), b"source")
                file.write_pre_activation("logical-file-id", b"candidate")
            self.assertEqual(output.read_bytes(), b"candidate")

    def test_available_content_is_immutable(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "input.bin"
            source.write_bytes(b"active")
            pycloud.configure({
                "permissions": [
                    "file.content.read",
                    "file.content.write_pre_activation",
                ],
                "content_frozen": True,
            })
            with patch.object(file, "_INPUT", str(source)):
                self.assertEqual(file.read(), b"active")
                with self.assertRaises(PermissionError):
                    file.write_pre_activation(b"forbidden")


if __name__ == "__main__":
    unittest.main()
