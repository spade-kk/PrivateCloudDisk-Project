"""转换器公共工具：JSON 序列化、正则拼接、转义。"""
from __future__ import annotations

import json


def json_dumps(obj) -> str:
    """稳定输出（ensure_ascii=False + 缩进），便于 diff（需求 14.17）。"""
    return json.dumps(obj, ensure_ascii=False, indent=2)


def regex_escape(text: str) -> str:
    """对字面量做正则转义，用于 TextMate/Monarch 中安全嵌入。"""
    import re
    return re.escape(text)


def alternative(items, escape=True) -> str:
    """把 token 列表拼成 `a|b|c`（长优先，避免 `|` 匹配 `||` 时被短先吞掉）。"""
    s = sorted(set(items), key=lambda x: (-len(x), x))
    parts = [regex_escape(x) if escape else x for x in s]
    return "|".join(parts)


def keyword_pattern(words) -> str:
    """`\b(?:w1|w2)\b`——关键字需词边界匹配，避免命中变量名子串（需求 5.5）。"""
    return r"\b(?:" + alternative(words) + r")\b"


IDENT = r"[A-Za-z_][A-Za-z0-9_-]*"
