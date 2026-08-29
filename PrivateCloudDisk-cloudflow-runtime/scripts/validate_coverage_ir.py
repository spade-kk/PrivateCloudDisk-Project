#!/usr/bin/env python3
"""CloudFlow 覆盖样例的离线 IR Schema 门禁。

需求关联：CLOUDFLOW-COVERAGE-001。CI 不依赖未锁定的 Python 第三方包；本脚本执行项目
schema 的必填字段、枚举和控制流契约子集，并额外检查 DAG 边与 $ref/$expr 结构。
完整 JSON Schema 文件仍是外部工具/IDE 的机器可读真源。
"""

from __future__ import annotations

import json
import sys
from pathlib import Path


NODE_TYPES = {"task", "plugin", "condition", "loop", "parallel", "try", "wait", "assert", "switch", "delay", "validate", "notify", "return", "break", "continue"}
VARIABLE_SOURCES = {"input", "local", "deferred"}


def fail(path: Path, message: str) -> None:
    raise ValueError(f"{path}: {message}")


def assert_value_structure(value: object, path: Path) -> None:
    if isinstance(value, list):
        for item in value:
            assert_value_structure(item, path)
    elif isinstance(value, dict):
        if "$ref" in value and (len(value) != 1 or not isinstance(value["$ref"], str)):
            fail(path, "$ref 必须是唯一且为 string 的字段")
        if "$expr" in value and len(value) != 1:
            fail(path, "$expr 必须是唯一字段")
        for item in value.values():
            assert_value_structure(item, path)


def validate(path: Path) -> None:
    document = json.loads(path.read_text(encoding="utf-8"))
    if document.get("apiVersion") != "workflow.cloudflow.io/v1" or document.get("kind") != "Workflow":
        fail(path, "顶层 apiVersion/kind 不符合 Workflow IR v1")
    spec = document.get("spec")
    if not isinstance(spec, dict) or not isinstance(spec.get("graph"), dict):
        fail(path, "缺少 spec.graph")
    variables = spec.get("variables")
    if not isinstance(variables, dict):
        fail(path, "spec.variables 必须是 object")
    for name, declaration in variables.items():
        if not isinstance(declaration, dict) or declaration.get("source") not in VARIABLE_SOURCES:
            fail(path, f"变量 {name} 缺少合法 source")
        if not isinstance(declaration.get("required"), bool) or not isinstance(declaration.get("type"), str):
            fail(path, f"变量 {name} 类型字段不合法")
        for key in ("default", "value"):
            if key in declaration:
                assert_value_structure(declaration[key], path)
    graph = spec["graph"]
    nodes, edges = graph.get("nodes"), graph.get("edges")
    if not isinstance(nodes, list) or not isinstance(edges, list):
        fail(path, "graph.nodes/edges 必须是 array")
    identifiers = set()
    for node in nodes:
        if not isinstance(node, dict) or not isinstance(node.get("id"), str) or not node["id"]:
            fail(path, "节点缺少 id")
        if node["id"] in identifiers:
            fail(path, f"节点 ID 重复：{node['id']}")
        identifiers.add(node["id"])
        if node.get("type") not in NODE_TYPES:
            fail(path, f"节点 {node['id']} 类型非法")
        for key in ("inputs", "outputs", "condition", "loopConfig", "errorHandler",
                    "notifyConfig", "onError", "dependsCondition"):
            if key in node and node[key] is not None:
                assert_value_structure(node[key], path)
        if node.get("type") == "loop":
            config = node.get("loopConfig")
            if not isinstance(config, dict) or config.get("kind") not in {"foreach", "while", "for", "for-range"}:
                fail(path, f"loop 节点 {node['id']} 缺少 kind")
        if node.get("type") == "wait" and not node.get("timeout"):
            fail(path, f"wait 节点 {node['id']} 缺少 timeout")
    for edge in edges:
        if not isinstance(edge, dict) or edge.get("from") not in identifiers or edge.get("to") not in identifiers:
            fail(path, f"边引用不存在节点：{edge}")


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        validate(Path(arg))
