"""云插件 Python 静态安全校验器（需求四 4.1-4.25）。

只解析 AST、绝不执行插件代码；AST 白名单是发布门禁，不替代容器沙箱。
错误信息只输出相对位置与用户可读描述，不包含宿主绝对路径（4.18/4.19/6.7）。
"""

from __future__ import annotations

import argparse
import ast
import json
import sys

# --------------------------------------------------------------------------- 白名单/黑名单
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
FORBIDDEN_CALLS = {
    # 动态执行（4.4）
    "eval", "exec", "compile", "__import__",
    # 文件与交互访问（4.13：文件必须经 pycloud SDK）
    "open", "input", "breakpoint", "help",
    # 宿主/解释器内省（4.5/4.9）
    "globals", "locals", "vars", "getattr", "setattr", "delattr",
}
FORBIDDEN_ATTRIBUTES = {
    "__class__", "__subclasses__", "__globals__", "__code__", "__closure__",
    "__dict__", "__mro__", "__bases__", "__module__", "__name__", "__builtins__",
}
# 允许的装饰器（4.16）：仅 pycloud SDK 标记与 Python 内置无副作用装饰器。
ALLOWED_DECORATORS = {
    "test", "capability", "staticmethod", "classmethod", "property",
    "abstractmethod", "cached_property", "functools.cache", "functools.lru_cache",
}

# --------------------------------------------------------------------------- 资源类上限
MAX_SOURCE_BYTES = 1024 * 1024
MAX_LINES = 5000
MAX_NODES = 20000
MAX_STRING_BYTES = 256 * 1024
MAX_FUNCTION_DEPTH = 4        # 4.7 函数调用/嵌套深度
MAX_LOOP_DEPTH = 4            # 4.7 循环嵌套深度
MAX_COMPLEXITY_FN = 40        # 4.23 单函数圈复杂度
MAX_TOTAL_COMPLEXITY = 260    # 4.23 全脚本圈复杂度

# 可疑字符串：命令执行 / 敏感路径 / 内网探测（4.14/15.13）
SUSPICIOUS_PATTERNS = (
    ("sh -c", "可疑命令执行片段"),
    ("bash -c", "可疑命令执行片段"),
    ("cmd /c", "可疑命令执行片段"),
    ("powershell", "可疑命令执行片段"),
    ("/etc/passwd", "敏感文件路径"),
    ("/etc/shadow", "敏感文件路径"),
    ("/proc/self", "宿主 proc 路径"),
    ("/var/run/docker.sock", "宿主 Docker Socket 路径"),
    ("/run/docker.sock", "宿主 Docker Socket 路径"),
    ("kubelet", "主机 Kubernetes 路径"),
    ("--privileged", "可疑提权参数"),
    ("mknod", "可疑设备创建参数"),
)


def finding(kind: str, node: ast.AST, message: str) -> dict:
    return {
        "type": kind,
        "line": getattr(node, "lineno", 0),
        "column": getattr(node, "col_offset", 0) + 1,
        "message": message,
    }


def result(valid: bool, kind: str, message: str, findings: list, metrics: dict) -> dict:
    return {
        "valid": valid,
        "error_type": kind,
        "line": 0,
        "column": 0,
        "message": message,
        "suggestion": "",
        "findings": findings,
        "metrics": metrics,
    }


# --------------------------------------------------------------------------- 结构度量
def function_depth_of(tree: ast.AST) -> int:
    """统计函数最大嵌套深度（4.7）。"""
    best = 0

    def walk(node: ast.AST, depth: int) -> None:
        nonlocal best
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            depth += 1
            best = max(best, depth)
        for child in ast.iter_child_nodes(node):
            walk(child, depth)

    walk(tree, 0)
    return best


def loop_depth_of(tree: ast.AST) -> int:
    """统计循环最大嵌套深度（4.7），while/for/async-for 均计入。"""
    best = 0

    def walk(node: ast.AST, depth: int) -> None:
        nonlocal best
        if isinstance(node, (ast.While, ast.For, ast.AsyncFor)):
            depth += 1
            best = max(best, depth)
        for child in ast.iter_child_nodes(node):
            walk(child, depth)

    walk(tree, 0)
    return best


def mccabe(tree: ast.AST) -> int:
    """圈复杂度：基础 1 + 每个 if/elif/for/while/with 与布尔二元运算 +1（4.23）。"""
    score = 1
    for node in ast.walk(tree):
        if isinstance(node, (ast.If, ast.For, ast.AsyncFor, ast.While, ast.With, ast.AsyncWith, ast.ExceptHandler)):
            score += 1
        elif isinstance(node, ast.BoolOp) and isinstance(node.op, (ast.And, ast.Or)):
            score += len(node.values) - 1 if len(node.values) > 1 else 0
    return score


def per_function_complexity(nodes: list) -> dict:
    """返回函数名 → 圈复杂度；供超标定位。"""
    mapping = {}
    for func in nodes:
        if isinstance(func, (ast.FunctionDef, ast.AsyncFunctionDef)):
            mapping[func.name] = mccabe(func)
    return mapping


def suspicious_string(value: str) -> str | None:
    for pattern, label in SUSPICIOUS_PATTERNS:
        if pattern in value:
            return label
    return None


# --------------------------------------------------------------------------- 主校验
def validate(source: str, entrypoint: str) -> dict:
    if len(source.encode("utf-8")) > MAX_SOURCE_BYTES:
        return result(False, "RESOURCE_LIMIT", "脚本超过 1 MiB", [], {})
    if source.count("\n") + 1 > MAX_LINES:
        return result(False, "RESOURCE_LIMIT", "脚本行数超过限制", [], {})
    try:
        tree = ast.parse(source, filename="<plugin>", mode="exec")
    except (SyntaxError, UnicodeError) as node:
        return {
            "valid": False,
            "error_type": "SYNTAX_ERROR",
            "line": getattr(node, "lineno", 0),
            "column": getattr(node, "offset", 0),
            "message": getattr(node, "msg", "语法错误"),
            "suggestion": "请修复语法错误后重新校验",
            "findings": [],
            "metrics": {},
        }

    nodes = list(ast.walk(tree))
    if len(nodes) > MAX_NODES:
        return result(False, "RESOURCE_LIMIT", "AST 节点数量超过限制", [], {"ast_nodes": len(nodes)})

    findings: list[dict] = []
    functions: set[str] = set()
    test_entrypoints: list[dict] = []
    capabilities: list[dict] = []

    func_depth = function_depth_of(tree)
    if func_depth > MAX_FUNCTION_DEPTH:
        findings.append({
            "type": "RESOURCE_LIMIT", "line": 0, "column": 0,
            "message": f"函数嵌套深度 {func_depth} 超过限制 {MAX_FUNCTION_DEPTH}",
        })
    loop_depth = loop_depth_of(tree)
    if loop_depth > MAX_LOOP_DEPTH:
        findings.append({
            "type": "RESOURCE_LIMIT", "line": 0, "column": 0,
            "message": f"循环嵌套深度 {loop_depth} 超过限制 {MAX_LOOP_DEPTH}",
        })
    total = mccabe(tree)
    if total > MAX_TOTAL_COMPLEXITY:
        findings.append({
            "type": "COMPLEXITY_LIMIT", "line": 0, "column": 0,
            "message": f"脚本圈复杂度 {total} 超过限制 {MAX_TOTAL_COMPLEXITY}",
        })
    fn_complexity = per_function_complexity(nodes)

    for node in nodes:
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            functions.add(node.name)
            if fn_complexity.get(node.name, 0) > MAX_COMPLEXITY_FN:
                findings.append(finding(
                    "COMPLEXITY_LIMIT", node,
                    f"函数 {node.name} 圈复杂度超过限制 {MAX_COMPLEXITY_FN}",
                ))
            for decorator in node.decorator_list:
                name = decorator_name(decorator)
                if name and name not in ALLOWED_DECORATORS:
                    findings.append(finding(
                        "SECURITY_VIOLATION", decorator,
                        f"装饰器不在白名单：{name}",
                    ))
                if isinstance(decorator, ast.Name) and decorator.id == "test":
                    test_entrypoints.append({"name": node.name, "line": node.lineno})
                elif (
                    isinstance(decorator, ast.Call)
                    and isinstance(decorator.func, ast.Name)
                    and decorator.func.id == "capability"
                    and len(decorator.args) == 1
                    and isinstance(decorator.args[0], ast.Constant)
                    and isinstance(decorator.args[0].value, str)
                ):
                    capabilities.append({
                        "name": decorator.args[0].value,
                        "function": node.name,
                        "line": node.lineno,
                    })
        if isinstance(node, ast.Import):
            for alias in node.names:
                root = alias.name.split(".", 1)[0]
                if root not in ALLOWED_MODULES:
                    findings.append(finding(
                        "SECURITY_VIOLATION", node, f"禁止导入模块：{root}"
                    ))
        elif isinstance(node, ast.ImportFrom):
            root = (node.module or "").split(".", 1)[0]
            if node.level or root not in ALLOWED_MODULES:
                findings.append(finding(
                    "SECURITY_VIOLATION", node, f"禁止导入模块：{root or 'relative'}"
                ))
        elif isinstance(node, ast.Global):
            findings.append(finding(
                "SECURITY_VIOLATION", node, "禁止使用 global 修改外部作用域"
            ))
        elif isinstance(node, ast.Nonlocal):
            findings.append(finding(
                "SECURITY_VIOLATION", node, "禁止使用 nonlocal 修改外部作用域"
            ))
        elif isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
            if node.func.id in FORBIDDEN_CALLS:
                findings.append(finding(
                    "SECURITY_VIOLATION", node, f"禁止调用：{node.func.id}"
                ))
        elif isinstance(node, ast.With):
            for item in node.items:
                if isinstance(item.context_expr, ast.Call) and isinstance(
                    item.context_expr.func, ast.Name
                ) and item.context_expr.func.id == "open":
                    findings.append(finding(
                        "SECURITY_VIOLATION", node,
                        "禁止用 with 打开文件，请使用 pycloud SDK",
                    ))
        elif isinstance(node, ast.Attribute):
            if node.attr in FORBIDDEN_ATTRIBUTES or node.attr.startswith("__"):
                findings.append(finding(
                    "SECURITY_VIOLATION", node, f"禁止访问敏感属性：{node.attr}"
                ))
        elif isinstance(node, ast.Constant):
            if isinstance(node.value, (str, bytes)) and len(node.value) > MAX_STRING_BYTES:
                findings.append(finding(
                    "RESOURCE_LIMIT", node, "字符串或字节常量超过限制"
                ))
            if isinstance(node.value, str):
                label = suspicious_string(node.value)
                if label is not None:
                    findings.append(finding(
                        "SUSPICIOUS_STRING", node, f"检测到可疑字符串：{label}"
                    ))
            if isinstance(node.value, int) and node.value.bit_length() > 4096:
                findings.append(finding(
                    "RESOURCE_LIMIT", node, "整数常量超过位数限制"
                ))

    function_name = entrypoint.rsplit("/", 1)[-1]
    if entrypoint.endswith(".py") and not functions:
        findings.append({
            "type": "ENTRYPOINT_INVALID", "line": 0, "column": 0,
            "message": "入口脚本未声明任何函数",
        })

    valid = not findings
    first = findings[0] if findings else {}
    return {
        "valid": valid,
        "error_type": first.get("type", ""),
        "line": first.get("line", 0),
        "column": first.get("column", 0),
        "message": "校验通过" if valid else first.get("message", "校验失败"),
        "suggestion": "" if valid else "请移除危险能力或降低代码复杂度",
        "findings": findings,
        "metrics": {
            "mode": "ast-only",
            "source_bytes": len(source.encode("utf-8")),
            "lines": source.count("\n") + 1,
            "ast_nodes": len(nodes),
            "functions": sorted(functions),
            "function_depth": func_depth,
            "loop_depth": loop_depth,
            "cyclomatic": total,
            "test_entrypoints": test_entrypoints,
            "capabilities": capabilities,
        },
    }


def decorator_name(node: ast.AST) -> str | None:
    """把装饰器规整为可比较名：func(...) -> func；a.b.c -> a.b.c；Name -> id。"""
    if isinstance(node, ast.Name):
        return node.id
    if isinstance(node, ast.Call):
        return decorator_name(node.func)
    if isinstance(node, ast.Attribute):
        base = decorator_name(node.value)
        if base is None:
            return node.attr
        return f"{base}.{node.attr}"
    return None


def main() -> None:
    parser = argparse.ArgumentParser(description="云插件 Python AST 静态校验")
    parser.add_argument("--ast-only", action="store_true", help="仅做 AST 校验，绝不执行")
    args, _ = parser.parse_known_args()
    try:
        request = json.load(sys.stdin)
        response = validate(str(request.get("source", "")), str(request.get("entrypoint", "")))
        response["metrics"]["ast_only"] = bool(args.ast_only)
    except Exception:
        response = result(False, "VALIDATOR_ERROR", "静态校验请求无效", [], {})
    json.dump(response, sys.stdout, ensure_ascii=False)


if __name__ == "__main__":
    main()
