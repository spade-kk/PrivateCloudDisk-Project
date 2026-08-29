import unittest
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from validate_python import validate


class PythonMarkerValidationTest(unittest.TestCase):
    def test_extracts_test_and_capability_markers_without_execution(self):
        report = validate(
            'from pycloud import capability, test\n'
            '@capability("file_analysis")\n'
            'def analyze(context):\n'
            '    return {}\n'
            '@test\n'
            'def test_analyze(context):\n'
            '    return analyze(context)\n',
            'src/main.py',
        )
        self.assertTrue(report["valid"])
        self.assertEqual(report["metrics"]["test_entrypoints"][0]["name"], "test_analyze")
        self.assertEqual(report["metrics"]["capabilities"][0]["name"], "file_analysis")

    def test_rejects_dynamic_execution(self):
        report = validate('def main(context):\n    return eval("1")\n', 'src/main.py')
        self.assertFalse(report["valid"])
        self.assertEqual(report["error_type"], "SECURITY_VIOLATION")



class PythonRealWorldFixtureTests(unittest.TestCase):
    """真实场景插件样本纳入发布门禁（2.20/8.20/9.19）。"""

    realworld_root = os.path.join(os.path.dirname(__file__), "..", "testdata", "plugins", "realworld")
    # 红色样本：仅用于证明拒绝路径，不作为合规样本。
    red_samples = {"malicious_import"}
    # .pcdpkg 受约束结构：realworld 插件源码位于 src/ 下。
    entry_files = ("src/main.py", "src/step_a.py", "src/step_b.py")

    def _load(self, plugin, entry):
        with open(
            os.path.join(self.realworld_root, plugin, entry), encoding="utf-8"
        ) as stream:
            source = stream.read()
        return validate(source, f"{plugin}/{entry}")

    def test_compliant_realworld_plugins_pass(self):
        seen = 0
        for plugin in sorted(os.listdir(self.realworld_root)):
            if plugin in self.red_samples:
                continue
            for entry in self.entry_files:
                if not os.path.exists(os.path.join(self.realworld_root, plugin, entry)):
                    continue
                report = self._load(plugin, entry)
                self.assertTrue(
                    report["valid"],
                    f"{plugin}/{entry} 应通过 AST 白名单："
                    + "; ".join(f.get("message", "") for f in report["findings"][:3]),
                )
                seen += 1
        self.assertGreater(seen, 0, "未发现任何合规 realworld 插件")

    def test_malicious_import_sample_rejected(self):
        report = self._load("malicious_import", "src/main.py")
        self.assertFalse(report["valid"])
        self.assertEqual(report["error_type"], "SECURITY_VIOLATION")
        messages = " ".join(f.get("message", "") for f in report["findings"])
        for module in ("os", "sys", "subprocess"):
            self.assertIn(module, messages)


class PythonSuspiciousStringTests(unittest.TestCase):
    def test_rejects_passwd_literal(self):
        report = validate(
            'def main(context):\n    return "/etc/passwd"\n',
            "main.py",
        )
        self.assertFalse(report["valid"])
        self.assertEqual(report["error_type"], "SUSPICIOUS_STRING")

    def test_rejects_dangerous_module_import(self):
        report = validate('import subprocess\n\ndef main(context):\n    return {}\n', "main.py")
        self.assertFalse(report["valid"])
        self.assertEqual(report["error_type"], "SECURITY_VIOLATION")


if __name__ == "__main__":
    unittest.main()
