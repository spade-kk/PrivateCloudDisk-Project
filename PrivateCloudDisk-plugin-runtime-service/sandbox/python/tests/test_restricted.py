"""运行时受限 Python 层（restricted.py）单元测试（插件安全改造 36.26-36.31）。

在宿主直接运行，无需 Docker：验证 import 白名单/黑名单运行时拦截、危险内置删除、
双下划线逃逸链改写、PEP 578 审计钩子阻断、stdout 截断与白名单模块正常执行。
"""

import io
import json
import os
import sys
import types
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import restricted  # noqa: E402


class RestrictedExecTest(unittest.TestCase):
    """以受限命名空间执行插件源码的用例。"""

    def _exec(self, source, function="main"):
        """在受限命名空间中执行 source，调用 main 并返回其返回值。"""
        return restricted.exec_plugin(
            source, "<test-plugin>", function, {"user_id": "u-1"},
            stdout=io.StringIO(), stderr=io.StringIO(),
        )

    def test_import_os_denied(self):
        with self.assertRaises(restricted.RestrictedError) as ctx:
            self._exec("import os\ndef main(c):\n    return os.getuid()\n")
        self.assertIn("禁止导入模块", str(ctx.exception))

    def test_import_subprocess_denied(self):
        with self.assertRaises(restricted.RestrictedError):
            self._exec("import subprocess\ndef main(c):\n    return 1\n")

    def test_import_sys_denied(self):
        with self.assertRaises(restricted.RestrictedError):
            self._exec("import sys\ndef main(c):\n    return 1\n")

    def test_import_pip_denied(self):
        with self.assertRaises(restricted.RestrictedError):
            self._exec("import pip\ndef main(c):\n    return 1\n")

    def test_relative_import_denied(self):
        with self.assertRaises(restricted.RestrictedError):
            self._exec("from . import math\ndef main(c):\n    return 1\n")

    def test_whitelist_modules_work(self):
        result = self._exec(
            "import json, math, statistics as st\n"
            "def main(c):\n"
            "    return {'n': math.floor(3.7), 'mean': round(st.mean([1,2,3]),2), 'loaded': json.dumps({'a':1})}\n"
        )
        self.assertEqual(result["n"], 3)
        self.assertEqual(result["mean"], 2.0)
        self.assertIn("a", result["loaded"])

    def test_eval_denied(self):
        with self.assertRaises(restricted.RestrictedError) as ctx:
            self._exec("def main(c):\n    return eval('1+1')\n")
        self.assertIn("受限环境禁用内置", str(ctx.exception))

    def test_exec_denied(self):
        with self.assertRaises(restricted.RestrictedError):
            self._exec("def main(c):\n    return exec('x=1')\n")

    def test_compile_denied(self):
        with self.assertRaises(restricted.RestrictedError):
            self._exec("def main(c):\n    return compile('1', '<s>', 'exec')\n")

    def test_open_denied(self):
        with self.assertRaises(restricted.RestrictedError):
            self._exec("def main(c):\n    return open('/etc/passwd').read()\n")

    def test_input_denied(self):
        with self.assertRaises(restricted.RestrictedError):
            self._exec("def main(c):\n    return input()\n")

    def test_globals_locals_vars_denied(self):
        for name in ("globals", "locals", "vars"):
            with self.subTest(name=name):
                with self.assertRaises(restricted.RestrictedError):
                    self._exec("def main(c):\n    return %s()\n" % name)

    def test_dunder_escape_denied(self):
        # 36.28：双下划线逃逸链在 AST 层被改写为 _pcd_deny。
        source = (
            "def main(c):\n"
            "    return ().__class__.__bases__[0].__subclasses__()\n"
        )
        with self.assertRaises(restricted.RestrictedError) as ctx:
            self._exec(source)
        self.assertIn("受限环境拒绝", str(ctx.exception))

    def test_dunder_getattr_denied(self):
        source = "def main(c):\n    return getattr((), '__class__')\n"
        with self.assertRaises(restricted.RestrictedError):
            self._exec(source)

    def test_dunder_dict_denied(self):
        source = "def main(c):\n    return {}.__dict__\n"
        with self.assertRaises(restricted.RestrictedError):
            self._exec(source)

    def test_pycloud_transport_private_attributes_denied(self):
        # CF-PLUGIN-UDS-001: a user plugin must not obtain the SDK's socket or
        # runner-injected per-instance token through module attributes.
        source = "def main(c):\n    return pycloud.capabilities._instance_token\n"
        with self.assertRaises(restricted.RestrictedError):
            self._exec(source)

    def test_audit_hook_blocks_os_via_sdk_attribute(self):
        # 36.13 加强：即使插件经由 SDK 模块属性拿到 os 句柄（如 pycloud.file.os），
        # PEP 578 审计钩子仍会在 os.system 调用瞬间阻断。
        fake_pycloud = types.ModuleType("pycloud")
        fake_pycloud.os = __import__("os")
        sys.modules["pycloud"] = fake_pycloud
        try:
            source = (
                "from pycloud import os\n"
                "def main(c):\n"
                "    return os.system('true')\n"
            )
            with self.assertRaises(restricted.RestrictedError) as ctx:
                restricted.exec_plugin(
                    source, "<t>", "main", {}, pycloud_module=fake_pycloud,
                    stdout=io.StringIO(), stderr=io.StringIO(),
                )
            self.assertIn("审计钩子阻断", str(ctx.exception))
        finally:
            sys.modules.pop("pycloud", None)

    def test_pycloud_injected_callable(self):
        # 36.31：插件调用 pycloud 能力函数可正常返回。
        fake_pycloud = types.ModuleType("pycloud")
        fake_pycloud.ping = lambda: {"ok": True}
        sys.modules["pycloud"] = fake_pycloud
        try:
            source = (
                "from pycloud import ping\n"
                "def main(c):\n"
                "    return ping()\n"
            )
            result = restricted.exec_plugin(
                source, "<t>", "main", {}, pycloud_module=fake_pycloud,
                stdout=io.StringIO(), stderr=io.StringIO(),
            )
            self.assertEqual(result, {"ok": True})
        finally:
            sys.modules.pop("pycloud", None)

    def test_stdout_truncated(self):
        # 36.21：超过输出上限后丢弃并保留截断标记。
        out = io.StringIO()
        source = "def main(c):\n    print('x' * 500)\n    return 1\n"
        restricted.exec_plugin(
            source, "<t>", "main", {}, stdout=out, stderr=io.StringIO(),
            recursion_limit=2000, log_limit_bytes=200,
        )
        content = out.getvalue()
        self.assertLessEqual(len(content), 200 + 64)
        self.assertIn("已按平台上限截断", content)

    def test_missing_function_denied(self):
        with self.assertRaises(restricted.RestrictedError):
            self._exec("def other(c):\n    return 1\n", function="missing")


class GuardSourceTest(unittest.TestCase):
    def test_banned_attr_rewritten(self):
        code = restricted.guard_source("def f():\n    return ().__class__\n", "<t>")
        self.assertIsNotNone(code)

    def test_banned_call_rewritten(self):
        code = restricted.guard_source("def f():\n    return open('/etc/passwd')\n", "<t>")
        self.assertIsNotNone(code)


class LimitedTextIOTest(unittest.TestCase):
    def test_writes_limited(self):
        inner = io.StringIO()
        stream = restricted.LimitedTextIO(inner, limit=10)
        stream.write("0123456789abcdefgh")
        self.assertLessEqual(len(inner.getvalue()), 64)
        self.assertTrue(stream._truncated)

    def test_flush_delegates(self):
        inner = io.StringIO()
        stream = restricted.LimitedTextIO(inner)
        stream.write("hi")
        stream.flush()
        self.assertIn("hi", inner.getvalue())


if __name__ == "__main__":
    unittest.main()
