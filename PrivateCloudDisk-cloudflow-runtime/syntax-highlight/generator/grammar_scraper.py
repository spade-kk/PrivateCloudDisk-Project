"""从 GRAMMAR.pest 源码文本提取 CloudFlow 语法 token。

实现方式（需求 14.4）：不依赖完整编译流程，仅用正则/轻量行扫描读取 .pest 文件：
- 提取所有被双引号包裹的字面量（关键字、操作符）。
- 定位注释、字符串、数字、布尔、时长、引用、标识符等语义规则，转成统一正则。
- 按 config 分类表归类关键字，未命中类别者登记为“未分类”（供 3.7/3.8 告警 / 同步检测）。

规则名 → 类别 的映射集中在本文件，保证 GRAMMAR.pest 变更时二次运行即可同步。
"""
from __future__ import annotations

import re
from typing import Dict, List, Tuple

import config as cfg


def _quoted_literals(source: str) -> List[str]:
    """提取所有 "..." 字面量（跳过注释行）。"""
    literals: List[str] = []
    for line in source.splitlines():
        stripped = line.strip()
        if stripped.startswith("//"):
            continue
        for match in re.finditer(r'"((?:[^"\\]|\\.)*)"', line):
            literals.append(match.group(1))
    return literals


# 结构性原子：来自 grammar 正则字符类/空白/转义/单位/注释起始等，不是面向用户的 DSL 关键字。
# 提取时会误入字面量列表，这里用内容谓词统一忽略（需求 3.4：只提取叶子 token，排除纯语法结构）。
ESCAPE_QUOTE_CHARS = set('\\"')
WS_ESCAPES = {"\\n", "\\r", "\\r\\n", "\\t"}
ATOM_LITERALS = {"E", "e", "d", "h", "m", "ms", "s", "_", "#"}


def _is_structural_atom(lit: str) -> bool:
    if lit in WS_ESCAPES or lit in ATOM_LITERALS:
        return True
    # 纯转义/引号原型（如 \"、\"\"\"、\\）与空白字符
    if lit and all(ch in ESCAPE_QUOTE_CHARS for ch in lit):
        return True
    if lit == " ":
        return True
    return False


def classify_literals(literals: List[str]) -> Dict[str, List[str]]:
    """把提取到的字面量按 config 分类；返回 {category: [tokens]} 并将未分类者放入 '__unclassified'。

    分类优先级（避免重复/漏分，需求 3.4/3.5）：
    1) 结构性原子（空白/转义/单位等）直接忽略；
    2) 语义关键字（control/declaration/...）优先分类——`workflow` 既是顶层关键字又是引用前缀，必须先归入关键字；
    3) 操作符与标点；
    4) 未命中任何类别者登记为未分类（供 3.7/3.8 告警）。
    """
    result: Dict[str, List[str]] = {cat: [] for cat in cfg.KEYWORDS}
    result["operator"] = []
    result["punctation"] = []
    result["__unclassified"] = []

    for lit in literals:
        if _is_structural_atom(lit):
            continue
        matched = False
        # 语义关键字优先（含 workflow 等同时是引用前缀的关键字）
        for cat, words in cfg.KEYWORDS.items():
            if lit in words:
                result[cat].append(lit)
                matched = True
                break
        if matched:
            continue
        # 引用前缀（vars/steps/workflow）未被归类为关键字时，由 references 规则单独高亮，不作为关键字
        if lit in cfg.REFERENCE_PREFIXES:
            continue
        # 操作符
        for op in cfg.OPERATORS.values():
            if lit in op:
                result["operator"].append(lit)
                matched = True
                break
        if matched:
            continue
        # 标点
        if lit in cfg.PUNCTUATION:
            result["punctation"].append(lit)
            continue
        result["__unclassified"].append(lit)

    for key in list(result):
        result[key] = sorted(set(result[key]))
    return result


def rule_for(source: str, name: str) -> str:
    """返回 .pest 中 `name = ...` 所在行（供规则正则识别）。"""
    for line in source.splitlines():
        if re.match(rf"^\s*{re.escape(name)}\s*=@?{{", line):
            return line.strip()
    return ""


def extract_comment(source: str) -> Dict[str, object]:
    """注释规则：CloudFlow 仅支持 `#` 单行注释。"""
    return {"line": {"start": "#", "scope": cfg.TOKEN_CATEGORIES["comment"]["scope"]}}


def extract_strings(source: str) -> Dict[str, object]:
    """字符串规则：双引号 string_value、三双引号 triple_string，含转义与模板插值。

    模板插值 ${...} 是 parser 层解析（字符串内部），在三种编辑器里都作为嵌套状态处理。
    """
    return {
        "double": {
            # \ 转义任意字符，直到下一个未被转义的双引号
            "pattern": r'"(?:\\.|[^"\\])*"',
            "scope": cfg.TOKEN_CATEGORIES["string"]["scope"],
        },
        "triple": {
            # 三双引号多行字符串，直到下三个连续双引号
            "pattern": r'"""(?s:.*?)"""',
            "scope": cfg.TOKEN_CATEGORIES["tripleString"]["scope"],
        },
        "interpolation": {
            "begin": "${",
            "end": "}",
            "scope": cfg.TOKEN_CATEGORIES["interpolation"]["scope"],
        },
    }


def extract_numbers(source: str) -> Dict[str, object]:
    """数字与时长规则（取自 grammar 的 number / duration / boolean）。"""
    return {
        "number": {
            # 兼容整数、小数、科学计数法
            "pattern": r"-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b",
            "scope": cfg.TOKEN_CATEGORIES["number"]["scope"],
        },
        "duration": {
            # ASCII_DIGIT+ ~ ("ms" | "s" | "m" | "h" | "d")
            "pattern": r"\b\d+(?:ms|s|m|h|d)\b",
            "scope": cfg.TOKEN_CATEGORIES["duration"]["scope"],
        },
        "boolean": {
            "words": cfg.KEYWORDS["literal"],
            "scope": cfg.TOKEN_CATEGORIES["literal"]["scope"],
        },
    }


def extract_references(source: str) -> Dict[str, object]:
    """引用规则：vars./steps./workflow. 前缀（来自 grammar 的 canonical_step_ref/vars_ref/system_ref）。"""
    refs = {}
    for prefix, cat in cfg.REFERENCE_PREFIXES.items():
        # system/workflow 引用也可以有后续段；统一 `prefix.ident(.ident)*`
        refs[prefix] = {
            "prefix": f"{prefix}.",
            "pattern": rf"\b{prefix}\.[A-Za-z_][A-Za-z0-9_-]*(?:\.[A-Za-z_][A-Za-z0-9_-]*)*",
            "scope": cfg.TOKEN_CATEGORIES[cat]["scope"],
        }
    return refs


def extract_identifiers(source: str) -> Dict[str, object]:
    """通用标识符：ident = (ASCII_ALPHA | "_") ~ (ASCII_ALPHANUMERIC | "_" | "-")*"""
    return {
        "ident": {
            "pattern": r"[A-Za-z_][A-Za-z0-9_-]*",
            "scope": "entity.name.variable.other.cloudflow",
        },
        "qualified": {
            # 点分标识符，用于 action 名 / 插件方法
            "pattern": r"[A-Za-z_][A-Za-z0-9_-]*(?:\.[A-Za-z_][A-Za-z0-9_-]*)+",
            "scope": "entity.name.function.cloudflow",
        },
    }


def scrape(source_text: str) -> Dict[str, object]:
    """主入口：解析 GRAMMAR.pest 文本，返回统一规范所需 token 数据。"""
    keywords = classify_literals(_quoted_literals(source_text))
    unclassified = keywords.pop("__unclassified", [])
    # operator / punctation 由 classify 一并收集，但统一规范中它们有独立顶层键，
    # 从 keywords 中移除，避免与 OPERATORS / PUNCTUATION 重复（需求 3.6 类别不重叠）。
    keywords.pop("operator", None)
    keywords.pop("punctation", None)

    return {
        "keywords": keywords,
        "operators": cfg.OPERATORS,
        "punctation": cfg.PUNCTUATION,
        "comments": extract_comment(source_text),
        "strings": extract_strings(source_text),
        "numbers": extract_numbers(source_text),
        "references": extract_references(source_text),
        "identifiers": extract_identifiers(source_text),
        "unclassifiedTokens": unclassified,
    }
