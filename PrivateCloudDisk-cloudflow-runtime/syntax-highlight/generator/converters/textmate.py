"""VS Code TextMate 语法生成器（需求 5.x）。

从统一规范生成 `cloudflow.tmLanguage.json`：
- patterns 顺序：注释 → 字符串 → 数字/时长 → 变量引用 → 操作符 → 关键字 → 标点 → 标识符兜底
- 关键字使用 `\\b` 词边界（需求 5.5）
- 三双引号字符串置于双引号之前，防止双引号 pattern 抢先匹配三双引号开头（需求 5.6）
- 字符串统一用 begin/end 包裹并嵌套 interpolation `${...}`（需求 5.7/5.8）
"""
from __future__ import annotations

from _common import IDENT, alternative, json_dumps, keyword_pattern

def generate(spec: dict, options: dict = None) -> str:
    options = options or {}
    meta = spec["$meta"]
    lang = meta["scopeName"]
    cats = spec["categories"]
    keywords = spec["keywords"]

    # ---- repository 构造 ----
    repo: dict = {}

    # 注释（单行 #）
    repo["comments"] = [{"match": r"#.*$", "name": cats["comment"]["scope"]}]

    # 字符串 + 插值
    tune = spec["strings"]
    interpolation_repo = {
        "match": r"\$\{",
    }
    repo["interpolation"] = [{
        "begin": r"\$\{",
        "end": r"\}",
        "name": tune["interpolation"]["scope"],
        "patterns": [{"include": "#numbers"}, {"include": "#references"},
                     {"include": "#keywords"}, {"include": "#operators"}],
    }]
    repo["strings"] = [
        {  # 三双引号多行字符串（放在双引号之前）
            "name": tune["triple"]["scope"],
            "begin": r'"""',
            "end": r'"""',
            "patterns": [{"include": "#interpolation"}],
        },
        {  # 双引号字符串，含 `\` 转义
            "name": tune["double"]["scope"],
            "begin": '"',
            "end": '"',
            "patterns": [{"include": "#interpolation"}],
        },
    ]

    # 数字 / 时长
    nums = spec["numbers"]
    repo["numbers"] = []
    if "duration" in nums and nums.get("duration", {}).get("pattern"):
        repo["numbers"].append({"match": nums["duration"]["pattern"],
                                "name": nums["duration"]["scope"]})
    repo["numbers"].append({"match": nums["number"]["pattern"],
                            "name": nums["number"]["scope"]})

    # 变量引用（vars./steps./workflow.）
    repo["references"] = []
    for prefix, ref in spec["references"].items():
        repo["references"].append({"match": ref["pattern"],
                                   "name": ref["scope"]})

    # 操作符（按组）
    repo["operators"] = []
    for group, ops in spec["operators"].items():
        if not ops:
            continue
        # 取该操作符组对应类别 scope（统一用 category.operator）
        repo["operators"].append({
            "match": alternative(ops),
            # 非贪婪无所谓；TextMate 匹配的是整个 op
        })
    # 合并成一个操作符 pattern（单一 scope）
    all_ops = [o for grp in spec["operators"].values() for o in grp]
    repo["operators"] = [{
        "match": alternative(all_ops),
        "name": cats["operator"]["scope"],
    }]

    # 关键字（按类别，使用各自 scope）
    repo["keywords"] = []
    for cat, words in keywords.items():
        if not words:
            continue
        scope = cats.get(cat, {}).get("scope", "keyword.other.cloudflow")
        repo["keywords"].append({
            "match": keyword_pattern(words),
            "name": scope,
        })

    # 标点
    repo["punctation"] = [{
        "match": alternative(spec["punctation"]),
        "name": cats["punctation"]["scope"],
    }]

    # 标识符兜底（局部变量 / 标识符）
    repo["identifiers"] = [{
        "match": r"\b" + IDENT + r"\b",
        "name": "entity.name.variable.other.cloudflow",
    }]

    grammar = {
        "$schema": "https://raw.githubusercontent.com/martinring/tmlanguage/master/tmlanguage.json",
        "name": meta["languageName"],
        "scopeName": lang,
        "fileTypes": [ext.lstrip(".") for ext in meta["fileExtensions"]],
        "patterns": [
            {"include": "#comments"},
            {"include": "#strings"},
            {"include": "#numbers"},
            {"include": "#references"},
            {"include": "#operators"},
            {"include": "#keywords"},
            {"include": "#punctation"},
            {"include": "#identifiers"},
        ],
        "repository": repo,
    }
    return json_dumps(grammar)


if __name__ == "__main__":
    import json, os, sys
    sys.path.insert(0, os.path.dirname(__file__))
    path = os.path.join(os.path.dirname(__file__), "..", "..", "build", "cloudflow.syntax-highlight.json")
    spec = json.load(open(path))
    print(generate(spec))
