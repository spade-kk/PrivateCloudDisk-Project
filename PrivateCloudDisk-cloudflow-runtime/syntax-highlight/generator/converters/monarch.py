"""Monaco Editor Monarch 语法生成器（需求 6.x）。

从统一规范生成 Monarch 规则对象（JSON，可供前端 import），也可输出 .ts/.js。
- tokenizer.states：root 为主，多行字符串与注释通过 `@`/push-pop 状态切换（需求 6.6）
- 字符串模板 `${...}` 用嵌套状态处理（需求 6.7）
- 变量引用 vars./steps./workflow. 各生成独立 token（需求 6.8）
- ignoreCase=false、defaultToken=""（需求 6.2）
颜色 scope 名称与 TextMate 一致，自动适配亮/暗主题（需求 6.14）。
"""
from __future__ import annotations

import json

from _common import IDENT, alternative, json_dumps, keyword_pattern


def _pat(regex: str) -> str:
    return regex


def generate(spec: dict, options: dict = None) -> str:
    options = options or {}
    meta = spec["$meta"]
    cats = spec["categories"]
    keywords = spec["keywords"]
    strs = spec["strings"]
    nums = spec["numbers"]

    # root 状态规则（顺序敏感：注释→字符串→数字→引用→操作符→关键字→标点→标识符）
    root: list = []

    # 注释 #
    root.append([r"#.*$", cats["comment"]["scope"]])

    # 三双引号字符串 -> 进入 triple 状态
    root.append([r'"""', {"token": strs["triple"]["scope"], "next": "@triple"}])
    # 双引号字符串 -> 进入 double 状态
    root.append([r'"', {"token": strs["double"]["scope"], "next": "@double"}])

    # 数字 / 时长
    if nums.get("duration", {}).get("pattern"):
        root.append([nums["duration"]["pattern"], nums["duration"]["scope"]])
    root.append([nums["number"]["pattern"], nums["number"]["scope"]])

    # 变量引用（每个前缀独立 token）
    for ref in spec["references"].values():
        root.append([ref["pattern"], ref["scope"]])

    # 操作符
    all_ops = [o for grp in spec["operators"].values() for o in grp]
    root.append([alternative(all_ops), cats["operator"]["scope"]])

    # 关键字（每个类别独立 token，长词优先）
    for cat, words in keywords.items():
        if not words:
            continue
        scope = cats.get(cat, {}).get("scope", "keyword.other.cloudflow")
        root.append([keyword_pattern(words), scope])

    # 标点
    root.append([alternative(spec["punctation"]), cats["punctation"]["scope"]])

    # 标识符兜底
    root.append([r"\b" + IDENT + r"\b", "entity.name.variable.other.cloudflow"])

    # 字符串内部状态
    interp_scope = strs["interpolation"]["scope"]
    _interp_rules = [
        [r"\}", {"token": interp_scope, "next": "@pop"}],
        [nums["number"]["pattern"], nums["number"]["scope"]],
    ]
    for ref in spec["references"].values():
        _interp_rules.append([ref["pattern"], ref["scope"]])
    _interp_rules.append([alternative(all_ops), cats["operator"]["scope"]])
    _interp_rules.append([r"\b" + IDENT + r"\b", "entity.name.variable.other.cloudflow"])
    interp_state = _interp_rules
    # 三双引号状态
    triple_state = [
        [r'"""', strs["triple"]["scope"], "@pop"],
        [r"\$\{", interp_scope, "@interp"],
        [r"[^\"\\]+", strs["triple"]["scope"]],
        [r"\\.", strs["triple"]["scope"]],
    ]
    # 双引号状态
    double_state = [
        [r'"', strs["double"]["scope"], "@pop"],
        [r"\$\{", interp_scope, "@interp"],
        [r"\\\$\{", strs["double"]["scope"]],  # 转义插值
        [r"\\.", strs["double"]["scope"]],
        [r"[^\"\\$]+", strs["double"]["scope"]],
    ]

    tokens: dict = {
        "defaultToken": "",
        "ignoreCase": False,
        "tokenizer": {
            "root": root,
            "triple": triple_state,
            "double": double_state,
            "interp": interp_state,
        },
    }
    return json_dumps(tokens)


if __name__ == "__main__":
    import os, sys
    sys.path.insert(0, os.path.dirname(__file__))
    path = os.path.join(os.path.dirname(__file__), "..", "..", "build", "cloudflow.syntax-highlight.json")
    spec = json.load(open(path))
    print(generate(spec))
