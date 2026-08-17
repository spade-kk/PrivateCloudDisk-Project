"""云插件 Python 静态安全校验器。

本文件只解析 AST，不执行插件代码。AST 白名单是发布门禁，不替代容器沙箱。
"""
from __future__ import annotations

import ast
import json
import sys

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
    "eval",
    "exec",
    "compile",
    "open",
    "input",
    "globals",
    "locals",
    "vars",
    "breakpoint",
    "help",
}
FORBIDDEN_ATTRIBUTES = {
    "__class__",
    "__subclasses__",
    "__globals__",
    "__code__",
    "__closure__",
    "__dict__",
    "__mro__",
    "__bases__",
}
MAX_SOURCE_BYTES = 1024 * 1024
MAX_LINES = 5000
MAX_NODES = 20000
MAX_STRING_BYTES = 256 * 1024


def finding(kind: str, node: ast.AST, message: str) -> dict:
    return {
        "type": kind,
        "line": getattr(node, "lineno", 0),
        "column": getattr(node, "col_offset", 0) + 1,
        "message": message,
    }


def validate(source: str, entrypoint: str) -> dict:
    if len(source.encode("utf-8")) > MAX_SOURCE_BYTES:
        return result(False, "RESOURCE_LIMIT", "脚本超过 1 MiB", [], {})
    if source.count("\n") + 1 > MAX_LINES:
        return result(False, "RESOURCE_LIMIT", "脚本行数超过限制", [], {})
    try:
        tree = ast.parse(source, filename="<plugin>", mode="exec")
    except SyntaxError as exc:
        return {
            "valid": False,
            "error_type": "SYNTAX_ERROR",
            "line": exc.lineno or 0,
            "column": exc.offset or 0,
            "message": exc.msg,
            "suggestion": "请修复语法错误后重新校验",
            "findings": [],
            "metrics": {},
        }

    nodes = list(ast.walk(tree))
    if len(nodes) > MAX_NODES:
        return result(False, "RESOURCE_LIMIT", "AST 节点数量超过限制", [], {
            "ast_nodes": len(nodes),
        })

    findings: list[dict] = []
    functions: set[str] = set()
    test_entrypoints: list[dict] = []
    capabilities: list[dict] = []
    for node in nodes:
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            functions.add(node.name)
            for decorator in node.decorator_list:
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
        elif isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
            if node.func.id in FORBIDDEN_CALLS:
                findings.append(finding(
                    "SECURITY_VIOLATION", node, f"禁止调用：{node.func.id}"
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
            if isinstance(node.value, int) and node.value.bit_length() > 4096:
                findings.append(finding(
                    "RESOURCE_LIMIT", node, "整数常量超过位数限制"
                ))
        elif isinstance(node, (ast.While, ast.AsyncFor)):
            # 不直接禁止循环，但在报告中提醒由运行时超时强制终止。
            pass

    function_name = entrypoint.rsplit("/", 1)[-1]
    if entrypoint.endswith(".py") and not functions:
        findings.append({
            "type": "ENTRYPOINT_INVALID",
            "line": 0,
            "column": 0,
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
            "source_bytes": len(source.encode("utf-8")),
            "lines": source.count("\n") + 1,
            "ast_nodes": len(nodes),
            "functions": sorted(functions),
            "test_entrypoints": test_entrypoints,
            "capabilities": capabilities,
        },
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


def main() -> None:
    try:
        request = json.load(sys.stdin)
        response = validate(str(request.get("source", "")), str(request.get("entrypoint", "")))
    except Exception:
        response = result(False, "VALIDATOR_ERROR", "静态校验请求无效", [], {})
    json.dump(response, sys.stdout, ensure_ascii=False)


if __name__ == "__main__":
    main()
