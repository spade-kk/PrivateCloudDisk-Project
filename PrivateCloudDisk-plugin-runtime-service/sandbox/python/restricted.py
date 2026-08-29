"""运行时受限 Python 层（设计文档 §8、插件运行时安全改造 36.x）。

AST 静态校验只是预检，不是安全边界；真正的 Python 运行时限制必须在沙盒容器内
由本模块强制实施。runner.py 通过 exec_plugin 执行插件源码，整个过程：

- 装入模块前按白名单/黑名单拦截 import（36.7-36.10）——os/sys/subprocess/socket/
  shutil/pathlib/ctypes/multiprocessing/threading/asyncio/pickle/marshal/shelve/
  importlib/inspect/pip 等一律拒绝；仅允许 pycloud 及 36.8 白名单模块。
- 覆盖/删除危险内置：eval/exec/compile/open/input/globals/locals/vars/getattr/
  setattr/delattr/breakpoint/help/__import__（36.11，作用于插件受限命名空间）。
- 双下划线逃逸链：通过 AST 改写拦截 __class__/__bases__/__subclasses__/
  __globals__/__mro__/__builtins__ 等敏感属性访问（36.12）。
- PEP 578 审计钩子（36.13）：对 os.system/exec/spawn/kill/fork、subprocess.*、
  socket.* 这些"即使拿到 os 句柄也不允许"的事件在运行时直接抛异常阻断，并记录
  security.log；import/open 由导入钩子与内置删除负责，这里仅记录可观测事件。
- 递归深度上限（36.18）、stdout/stderr 长度截断（36.21）。

约束：受限范围是"插件命名空间"。SDK（runner.py/pycloud）自身仍使用 os/open 等，
因此 import 内置与安全 builtins 必须注入到插件自己的 globals，而非替换进程级
builtins。插件可能经由 SDK 模块属性（如 pycloud.file.os）拿到 os 句柄，因此
PEP 578 审计钩子是 import 白名单之外的第二道强制边界，不能只做记录。
"""

from __future__ import annotations

import ast
import builtins
import io
import os
import sys
from typing import Any, Callable

# 白名单模块（36.8）——以顶层模块名判定，pycloud 及其子包整体放行。
ALLOWED_MODULES = {
    "pycloud",
    "math",
    "json",
    "datetime",
    "collections",
    "itertools",
    "functools",
    "statistics",
    "decimal",
}

# 黑名单模块（36.9）；pip/importlib 一并拒绝（36.10/36.9）。
BLOCKED_MODULES = {
    "os", "sys", "subprocess", "socket", "shutil", "pathlib", "ctypes",
    "multiprocessing", "threading", "asyncio", "pickle", "marshal", "shelve",
    "importlib", "inspect", "pip", "pkg_resources", "setuptools", "builtins",
    "code", "pty", "mmap", "curses", "tkinter", "webbrowser", "platform",
}

# 危险内置函数（36.11）——从插件受限 global 的 __builtins__ 中移除。
BANNED_BUILTINS = {
    "eval", "exec", "compile", "open", "input", "globals", "locals", "vars",
    "getattr", "setattr", "delattr", "breakpoint", "help", "__import__",
}

# 双下划线逃逸链敏感属性（36.12）——AST 改写为 _pcd_deny 调用。
BANNED_ATTRS = {
    "__class__", "__bases__", "__subclasses__", "__globals__", "__mro__",
    "__builtins__", "__getattr__", "__setattr__", "__delattr__",
    "__getattribute__", "__import__", "__loader__", "__spec__", "__dict__",
    "__init_subclass__", "__reduce__", "__reduce_ex__",
}

# 默认递归深度上限（36.18）。
DEFAULT_RECURSION_LIMIT = 2000

# 日志输出上限（36.21）。
DEFAULT_LOG_LIMIT_BYTES = 64 * 1024

# PEP 578 审计钩子严格阻断的事件前缀（36.13）。
# 这些事件在 SDK 正常运行中绝不出现；即使插件经由 SDK 属性绕开 import 拿到 os/
# socket/subprocess 句柄，这里仍会在调用瞬间抛错，形成 import 之外的强制边界。
_BLOCK_AUDIT_EVENTS = {
    "os.system", "os.exec", "os.posix_spawn", "os.spawn",
    "os.kill", "os.killpg", "os.fork", "os.forkpty",
    "subprocess.Popen", "subprocess.run", "subprocess.call",
    "subprocess.check_call", "subprocess.check_output",
    "socket.bind", "socket.send",
    "socket.sendto", "socket.setsockopt", "socket.listen", "socket.accept",
    "pty.fork", "os.popen",
}

# 审计钩子仅记录的事件（可观测，拦截由 import/builtin 层负责）。
_OBSERVE_AUDIT_EVENTS = {"open", "import", "socket.gethostbyname"}


class RestrictedError(RuntimeError):
    """受限命名空间中触发的安全拦截。"""


def pcd_deny(reason: str) -> None:
    raise RestrictedError(f"受限环境拒绝：{reason}")


class LimitedTextIO(io.TextIOBase):
    """截断式 stdout/stderr 包装：超限后丢弃并标记 truncated（36.21）。"""

    def __init__(self, inner: Any, limit: int = DEFAULT_LOG_LIMIT_BYTES) -> None:
        self._inner = inner
        self._limit = limit
        self._used = 0
        self._truncated = False

    def write(self, text: str) -> int:
        text = str(text)
        if self._truncated:
            # 已经截断：只保留一个明确的截断标记，避免日志无限放大。
            if not getattr(self, "_marker_written", False):
                self._marker_written = True
                try:
                    self._inner.write("[plugin输出已按平台上限截断]\n")
                except Exception:
                    pass
            return len(text)
        remaining = self._limit - self._used
        if remaining > 0:
            chunk = text[:remaining]
            try:
                self._inner.write(chunk)
                self._used += len(chunk)
            except Exception:
                pass
        if len(text) > remaining:
            self._truncated = True
        return len(text)

    def flush(self) -> None:
        try:
            self._inner.flush()
        except Exception:
            pass


class _GuardImport:
    """以顶层模块名执行的导入拦截器（36.7/36.9/36.10）。"""

    def __init__(self, real_import: Callable[..., Any]) -> None:
        self._real = real_import

    def __call__(
        self,
        name: str,
        globals_: dict | None = None,
        locals_: dict | None = None,
        fromlist: tuple = (),
        level: int = 0,
    ) -> Any:
        if level != 0:
            # 相对导入一律拒绝，避免经由包相对路径访问宿主模块。
            pcd_deny(f"禁止相对导入：{name}")
        top = name.split(".")[0]
        if top in BLOCKED_MODULES:
            pcd_deny(f"禁止导入模块：{top}")
        if top not in ALLOWED_MODULES:
            if top == "pcd_user_plugin":
                return self._real(name, globals_, locals_, fromlist, level)
            pcd_deny(f"模块不在白名单：{top}")
        return self._real(name, globals_, locals_, fromlist, level)


def safe_getattr(obj: Any, name: str, default: Any = ...) -> Any:
    """受限 getattr：拒绝敏感双下划线属性（36.11 加强）。"""
    if name in BANNED_ATTRS or name.startswith("_"):
        pcd_deny(f"禁止访问属性：{name}")
    return builtins.getattr(obj, name) if default is ... else builtins.getattr(obj, name, default)


def safe_setattr(obj: Any, name: str, value: Any) -> None:
    if name in BANNED_ATTRS or name.startswith("_"):
        pcd_deny(f"禁止设置属性：{name}")
    builtins.setattr(obj, name, value)


def safe_delattr(obj: Any, name: str) -> None:
    if name in BANNED_ATTRS or name.startswith("_"):
        pcd_deny(f"禁止删除属性：{name}")
    builtins.delattr(obj, name)


def guarded_builtins() -> dict:
    """构造插件专用受限 __builtins__（36.11）。"""
    namespace = dict(builtins.__dict__)
    for name in BANNED_BUILTINS:
        namespace.pop(name, None)
    namespace["__import__"] = _GuardImport(builtins.__import__)
    namespace["getattr"] = safe_getattr
    namespace["setattr"] = safe_setattr
    namespace["delattr"] = safe_delattr
    return namespace


class _AttrGuard(ast.NodeTransformer):
    """AST 改写：敏感属性访问与危险内置调用 → _pcd_deny（36.12/36.11）。"""

    def visit_Attribute(self, node: ast.Attribute) -> ast.AST:
        self.generic_visit(node)
        # [CF-PLUGIN-UDS-001] PyCloud's transport/token internals must not be
        # reachable from user code. A plugin may only call the documented SDK
        # functions, never obtain its raw socket or runner-injected credential.
        if node.attr in BANNED_ATTRS or (node.attr.startswith("_") and _is_sdk_attr(node)):
            return self._deny_call(f"双下划线逃逸属性：{node.attr}")
        return node

    def visit_Call(self, node: ast.Call) -> ast.AST:
        self.generic_visit(node)
        if isinstance(node.func, ast.Name) and node.func.id in {
            "eval", "exec", "compile", "open", "input", "globals", "locals",
            "vars", "breakpoint", "help", "__import__",
        }:
            return self._deny_call(f"受限环境禁用内置：{node.func.id}")
        return node

    def _deny_call(self, reason: str) -> ast.Call:
        deny = ast.Name(id="_pcd_deny", ctx=ast.Load())
        return ast.Call(func=deny, args=[ast.Constant(value=reason)], keywords=[])


def _is_sdk_attr(node: ast.Attribute) -> bool:
    current: ast.AST = node.value
    while isinstance(current, ast.Attribute):
        current = current.value
    return isinstance(current, ast.Name) and current.id in {"pycloud", "capabilities"}


def guard_source(source: str, filename: str = "<plugin>") -> Any:
    """返回改写后的插件代码对象（危险内置/逃逸属性替换为 _pcd_deny 调用）。"""
    tree = ast.parse(source, filename=filename, mode="exec")
    tree = _AttrGuard().visit(tree)
    ast.fix_missing_locations(tree)
    return compile(tree, filename, "exec")


# 审计钩子是否已安装（每个进程一次）。
_audit_installed = False


def _install_audit_hook() -> bool:
    """安装 PEP 578 审计钩子（36.13）：阻断 4 类危险事件 + 记录安全日志。

    ``open``/``import`` 仅观测（阻断职责在 AST/内置/导入钩子），否则 SDK 自身的
    文件读写与模块加载会被误伤；os.system/subprocess/socket/fork 等事件在 SDK 正常
    流程中绝不可能出现，直接抛 RestrictedError，形成 import 白名单之外的第二道边界。
    """
    global _audit_installed
    if _audit_installed:
        return True
    try:
        log_path: str | None = None
        try:
            log_dir = "/workspace/work"
            os.makedirs(log_dir, exist_ok=True)
            log_path = os.path.join(log_dir, "security.log")
        except Exception:
            log_path = None

        def _log_security(event: str, args: tuple) -> None:
            if log_path:
                try:
                    with open(log_path, "a", encoding="utf-8") as stream:
                        stream.write("security_event=%s args=%s\n" % (
                            event,
                            ", ".join(str(a) for a in args[:6]),
                        ))
                except Exception:
                    pass

        def _hook(event: str, args: tuple) -> None:
            try:
                # CF-PLUGIN-UDS-001: UDS is a deliberately narrow exception to
                # the no-network sandbox rule. Only the fixed bind mount is
                # accepted; TCP, other Unix paths and connect_ex are denied.
                if event in {"socket.connect", "socket.connect_ex"}:
                    address = args[1] if len(args) > 1 else None
                    if address == "/runtime/runtime.sock":
                        return
                    _log_security(event, args)
                    pcd_deny(f"审计钩子阻断非 Runtime Socket 连接：{event}")
                blocked = any(
                    event == prefix or event.startswith(prefix)
                    for prefix in _BLOCK_AUDIT_EVENTS
                )
                if blocked:
                    _log_security(event, args)
                    pcd_deny(f"审计钩子阻断危险事件：{event}")
                if event in _OBSERVE_AUDIT_EVENTS:
                    _log_security(event, args)
            except RestrictedError:
                # 阻断事件必须向上传播给插件/runner。
                raise
            except Exception:
                # 审计钩子自身绝不允许抛出非受限错误，避免污染进程。
                pass

        sys.addaudithook(_hook)
        _audit_installed = True
        return True
    except Exception:
        return False


def exec_plugin(
    source: str,
    filename: str,
    function_name: str,
    plugin_context: Any,
    recursion_limit: int = DEFAULT_RECURSION_LIMIT,
    log_limit_bytes: int = DEFAULT_LOG_LIMIT_BYTES,
    stdout: Any = None,
    stderr: Any = None,
    pycloud_module: Any = None,
) -> Any:
    """在受限命名空间中编译并执行插件源码，返回函数结果。

    - 递归深度上限（36.18）
    - 受限 builtins 与 import 拦截（36.7-36.12）
    - PEP 578 审计钩子（36.13）
    - stdout/stderr 截断（36.21）
    """
    real_stdout = stdout if stdout is not None else sys.stdout
    real_stderr = stderr if stderr is not None else sys.stderr
    limited_out = LimitedTextIO(real_stdout, limit=log_limit_bytes)
    limited_err = LimitedTextIO(real_stderr, limit=log_limit_bytes)
    previous_recursion = sys.getrecursionlimit()
    old_stdout, old_stderr = sys.stdout, sys.stderr
    sys.setrecursionlimit(max(100, recursion_limit))
    sys.stdout, sys.stderr = limited_out, limited_err
    _install_audit_hook()
    try:
        code = guard_source(source, filename)
        safe_builtins = guarded_builtins()
        sdk = pycloud_module
        if sdk is None and "pycloud" in sys.modules:
            sdk = sys.modules["pycloud"]
        plugin_globals = {
            "__name__": "pcd_user_plugin",
            "__file__": filename,
            "__builtins__": safe_builtins,
            "_pcd_deny": pcd_deny,
            "pycloud": sdk,
        }
        exec(code, plugin_globals)  # noqa: S102 - 已通过 AST 守卫与受限 builtins
        function = plugin_globals.get(function_name)
        if not callable(function):
            pcd_deny(f"入口函数不存在或不可调用：{function_name}")
        return function(plugin_context)
    finally:
        sys.stdout, sys.stderr = old_stdout, old_stderr
        sys.setrecursionlimit(previous_recursion)
