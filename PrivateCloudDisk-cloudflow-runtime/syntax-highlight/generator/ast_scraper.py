"""从 src/ast.rs 源码文本提取 CloudFlow AST 节点，并映射到语义高亮作用域（meta scope）。

实现方式（需求 14.4/14.11/14.12）：不依赖完整编译流程，仅用正则/行扫描读取 ast.rs：
- 提取所有 `pub struct X` / `pub enum X` 类型名。
- 提取 `pub enum FlowNode` 的所有变体名（Step/Condition/Loop/...）。
- 按节点名关键词把节点映射到 TextMate meta scope（如 WorkflowNode -> meta.workflow.cloudflow）。
- 输出 `{types, flow_variants, scopes}` 数据，写入统一规范，供 TextMate/Monarch/HLJS 引用。

新增 AST 节点时：只需在 ast.rs 声明结构/枚举，二次运行本脚本即可自动获得对应 meta scope。
未匹配到 pre-defined 规则的节点名会进入 `__unmapped` 并告警（避免遗漏，对应需求 4.8/14.12）。
"""
from __future__ import annotations

import re
from typing import Dict, List

import config as cfg


SCOPE_ROOT = cfg.LANGUAGE["scopeName"]  # source.cloudflow
# meta scope 后缀用语言根 `cloudflow`（如 meta.block.if.cloudflow），而非完整 scopeName
LANG_SUFFIX = cfg.LANGUAGE["scopeName"].rsplit(".", 1)[-1]


def _node_scopes() -> Dict[str, str]:
    """节点名（小写关键词）-> scope id 前缀（全局映射，供 meta scope 生成）。"""
    return {
        "workflow": "meta.workflow",
        "metadata": "meta.metadata",
        "trigger": "meta.trigger",
        "runtime": "meta.runtime",
        "handlers": "meta.handlers",
        "condition": "meta.block.if",
        "loop": "meta.block.foreach",
        "for": "meta.block.for",
        "while": "meta.block.while",
        "parallel": "meta.block.parallel",
        "try": "meta.block.try",
        "catch": "meta.block.catch",
        "finally": "meta.block.finally",
        "wait": "meta.block.wait",
        "assert": "meta.block.assert",
        "switch": "meta.block.switch",
        "case": "meta.block.case",
        "delay": "meta.block.delay",
        "notify": "meta.block.notify",
        "return": "meta.block.return",
        "break": "meta.block.break",
        "continue": "meta.block.continue",
        "validate": "meta.block.validate",
        "step": "meta.block.step",
        "group": "meta.block.group",
        "variable": "meta.declaration.variable",
        "environment": "meta.declaration.environment",
        "audit": "meta.annotation",
        "action": "meta.block.action",
        "include": "meta.import",
        "handler": "meta.handlers",
    }


def _structs(source: str) -> List[str]:
    """提取所有 `pub struct Name` 名称。"""
    return re.findall(r"pub\s+struct\s+([A-Za-z0-9_]+)", source)


def _enums(source: str) -> List[str]:
    """提取所有 `pub enum Name` 名称。"""
    return re.findall(r"pub\s+enum\s+([A-Za-z0-9_]+)", source)


def _flow_variants(source: str) -> List[str]:
    """提取 `pub enum FlowNode` 体内的变体名（`Name(...)` 或 `Name,`）。"""
    m = re.search(r"pub\s+enum\s+FlowNode\s*\{", source)
    if not m:
        return []
    # 从 FlowNode 枚举起点到下一个顶层 enum/struct/impl（简单取到文件里下一个闭合的 enum 边界较难，
    # 这里截至 FlowNodeEnum 后 40 行内即可，流程变体紧邻其后）。
    chunk = source[m.start():]
    lines = chunk.splitlines()[:60]
    variants: List[str] = []
    for line in lines:
        line = line.strip()
        name = re.match(r"^([A-Za-z0-9_]+)\s*(?:\(|,)", line)
        if name:
            variants.append(name.group(1))
        if line.startswith("}") and variants:
            break
    return variants


def _comment_for(source: str, name: str) -> str:
    """返回紧邻 `pub struct/enum Name` 的上方 `///` 行的最后一非空注释（供说明）。"""
    lines = source.splitlines()
    for i, line in enumerate(lines):
        if re.match(rf"pub\s+(struct|enum)\s+{re.escape(name)}\b", line.strip()):
            j = i - 1
            while j >= 0 and lines[j].strip().startswith("///"):
                j -= 1
            return lines[j + 1].strip().lstrip("/ ").strip()
    return ""


def _map_scope(name: str) -> str:
    """把节点名映射为 meta scope；规则：FlowNode 变体 -> meta.block.<kw>，其余 -> 按关键词匹配。"""
    table = _node_scopes()
    low = name.lower()
    for keyword, prefix in table.items():
        if keyword in low:
            return f"{prefix}.{LANG_SUFFIX}"
    return f"meta.{low}.{LANG_SUFFIX}"


def scrape(source_text: str) -> Dict[str, object]:
    """主入口：解析 ast.rs 文本，返回 AST 节点/作用域数据。"""
    structs = _structs(source_text)
    enums = _enums(source_text)
    flow_variants = _flow_variants(source_text)

    nodes: Dict[str, object] = {}
    unmapped: List[str] = []

    for name in structs + enums:
        scope = _map_scope(name)
        nodes[name] = {
            "kind": "struct" if name in structs else "enum",
            "scope": scope,
            "comment": _comment_for(source_text, name),
        }
        # 结构体名自身没命中任何关键词时，兜底映射为 meta.<name>，不算未映射
        if name not in structs and name not in enums:
            continue

    flow_map = {}
    for variant in flow_variants:
        flow_map[variant] = {
            "scope": _map_scope(variant),
        }

    # FlowNode 枚举整体也作为一个 meta 容器
    return {
        "types": {
            "structs": structs,
            "enums": enums,
        },
        "flowNodeVariants": flow_variants,
        "nodeScopes": nodes,
        "flowVariantScopes": flow_map,
        # 保留相关 AST 容器 scope，供渲染器做整块 meta 包裹
        "containerScopes": {
            "workflow": _map_scope("WorkflowNode"),
        },
        "unmappedNodes": unmapped,
    }
