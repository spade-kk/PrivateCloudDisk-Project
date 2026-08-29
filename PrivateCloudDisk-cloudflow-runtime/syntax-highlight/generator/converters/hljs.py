"""Highlight.js 语言定义生成器（需求 7.x）。

从统一规范生成 `cloudflow.hljs.js`（UMD，兼容 ES Module 与 CommonJS，需求 7.14）：
- 注册语言名 cloudflow，aliases 含 flow/cloudflow
- keywords：control+declaration+type+modifier+function 归入 `keyword`；literal 归入 `literal`
- contains：注释 / 数字 / 时长 / 字符串（双引号+三双引号+插值）/ 操作符 / 变量引用 / 标点
- 输出自带 `hljs.registerLanguage('cloudflow', ...)`，网页可直接引入（需求 7.11/7.12）

说明：highlight.js 将字符串值当作正则解释；为避免 Python 字符串拼接转义混乱，
本模块先用 Python 数据结构构建语言定义，再用 `_js_from_py` 序列化为 JS 字面量。
"""
from __future__ import annotations

import json

from _common import IDENT, alternative, regex_escape


def _js_str(text: str) -> str:
    """生成 JS 单引号字符串（转义反斜杠与单引号）。"""
    return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'"


def _js_from_py(obj) -> str:
    """把 Python 对象（dict/list/str/int/bool/None）序列化为 JS 字面量。

    HLJS 的 `begin`/`end` 用正则字符串，需保证输出为 JS 字符串字面量且保留正则转义。
    为简洁，这里对字符串统一输出双引号 JSON 转义形式（JS 兼容）。
    """
    return json.dumps(obj, ensure_ascii=False)


def generate(spec: dict, options: dict = None) -> str:
    options = options or {}
    meta = spec["$meta"]
    keywords = spec["keywords"]
    strs = spec["strings"]
    nums = spec["numbers"]

    # ---- 关键字按 HLJS 关键字类区分（与 TextMate/Monarch 的类别 scope 对应，需求：类别不坍塌）----
    # HLJS 支持多个关键字类，分别映射到不同 CSS class（hljs-keyword/type/literal/built_in）。
    # 控制流 / 声明 / 限定词 -> keyword；类型 -> type；布尔 -> literal；
    # 管道函数 filter/map/reduce + 仅 vars（非关键字的引用前缀）-> built_in。
    # 注意：workflow / steps 已是声明关键字，只进 keyword 类，避免重复进 built_in 造成双类。
    keyword_words = keywords.get("control", []) + keywords.get("declaration", []) + keywords.get("modifier", [])
    type_words = keywords.get("type", [])
    literal_words = keywords.get("literal", [])
    builtin_words = keywords.get("function", []) + [p for p in spec["references"].keys() if p not in keyword_words]

    # ---- contains 规则（顺序敏感：注释→字符串→数字→引用→操作符→标点）----
    contains = []

    contains.append({"className": "comment", "begin": "#", "end": "$"})

    # 字符串插值（${...}）。注意：highlight.js 中 contains 里的每个子规则都必须有 begin/end；
    # 不能放 { scope: 'string' } 这类无 begin/end 的“兜底”规则——它会导致外层字符串的 end 永远不触发，
    # 使 "..." 之后的全部内容被着色成字符串（实测复现）。外层字符串本身用 className/scope 着色即可。
    interpolation = [
        {"begin": r"\$\{", "end": r"\}", "scope": "variable"},
    ]

    # 三双引号字符串（优先于双引号，防止 `"` 抢先匹配 `"""`）
    contains.append({"className": "string", "begin": '"""', "end": '"""',
                     "contains": interpolation})
    # 双引号字符串。注意：contains 里只能放有 begin/end 的插值规则，绝不能再加
    # { scope: 'string' } 或仅 { begin } 的“兜底/转义”规则——它们会把模式压栈且永远不 pop，
    # 导致外层字符串的 end 永远不触发（实测复现：`"..."` 之后的全部内容都被着色成字符串）。
    contains.append({"className": "string", "begin": '"', "end": '"',
                     "contains": interpolation})

    if nums.get("duration", {}).get("pattern"):
        contains.append({"className": "number", "begin": nums["duration"]["pattern"]})
    contains.append({"className": "number", "begin": nums["number"]["pattern"]})

    # 变量引用（vars./steps./workflow.）
    for ref in spec["references"].values():
        contains.append({"className": "variable", "begin": ref["pattern"]})

    # 操作符
    all_ops = [o for grp in spec["operators"].values() for o in grp]
    contains.append({"className": "operator", "begin": alternative(all_ops)})

    # 标点
    contains.append({"className": "punctuation", "begin": alternative(spec["punctation"])})

    contains_js = ",\n".join(_js_from_py(c) for c in contains)

    lang_id = meta["languageId"]
    name = meta["languageName"]
    keyword_js = _js_str(" ".join(keyword_words))
    type_js = _js_str(" ".join(type_words))
    literal_js = _js_str(" ".join(literal_words))
    builtin_js = _js_str(" ".join(builtin_words))

    return f"""// 由统一规范 cloudflow.syntax-highlight.json 自动生成（请勿手动修改）。
// 生成器：syntax-highlight/generator/converters/hljs.py
// 更新方式：修改 GRAMMAR.pest / AST.rs 后运行 build_spec.py，再运行 convert.py --format hljs。
(function (global, factory) {{
  typeof exports === 'object' && typeof module !== 'undefined'
    ? module.exports = factory(require('highlight.js'))
    : typeof define === 'function' && define.amd
      ? define(['highlight.js'], factory)
      : (global = global || globalThis, global.cloudflowHighlight = factory(global.hljs));
}})(this, function (hljs) {{
  var def = {{
    name: {_js_str(name)},
    aliases: {_js_from_py(['cloudflow', 'flow'])},
    keywords: {{
      keyword: {keyword_js},
      type: {type_js},
      literal: {literal_js},
      built_in: {builtin_js}
    }},
    contains: [
{contains_js}
    ]
  }};
  if (hljs && typeof hljs.registerLanguage === 'function') {{
    try {{ hljs.registerLanguage({_js_str(lang_id)}, function () {{ return def; }}); }}
    catch (e) {{ /* 语言已注册或 hljs 不可用时静默 */ }}
  }}
  return def;
}});
"""


if __name__ == "__main__":
    import os
    import sys
    sys.path.insert(0, os.path.dirname(__file__))
    path = os.path.join(os.path.dirname(__file__), "..", "..", "build", "cloudflow.syntax-highlight.json")
    spec = json.load(open(path))
    print(generate(spec))
