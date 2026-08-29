#!/usr/bin/env python3
"""CloudFlow 统一代码补全规范生成器（需求 15.x）。

与语法高亮规范（build_spec.py）平行，读取同一解析源：
- GRAMMAR.pest：提取关键字、操作符、标识符、变量引用前缀；
- AST.rs：提取 AST 节点类型，用于校验/呈现结构模板；
- config.py：人工知识库（内置函数白名单、类型、触发器、异常白名单、片段、错误码）。

产出唯一事实来源 `build/cloudflow.completion.json`，供 VS Code / Monaco Editor
的补全、签名帮助、括号配对、缩进与错误诊断使用（需求 15.1-15.2）。

用法：
    python3 syntax-highlight/generator/completion_builder.py [--force] [--verbose]
"""
from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ast_scraper  # noqa: E402
import config as cfg  # noqa: E402
import grammar_scraper  # noqa: E402

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HERE = os.path.dirname(os.path.abspath(__file__))
GRAMMAR_PATH = os.path.join(REPO_ROOT, "src", "grammar.pest")
AST_PATH = os.path.join(REPO_ROOT, "crates", "cloudflow-engine-core", "src", "ast.rs")
OUT_DIR = os.path.join(HERE, "..", "build")
COMPLETION_OUT = os.path.join(OUT_DIR, "cloudflow.completion.json")
SCHEMA_DIR = os.path.join(HERE, "..", "schema")
SCHEMA_PATH = os.path.join(SCHEMA_DIR, "cloudflow.completion.schema.json")

# 关键字 → 补全 kind（monaco 通用 kind 名）映射
_KEYWORD_KIND = {
    "control": "keyword",
    "declaration": "keyword",
    "function": "function",
    "type": "type",
    "literal": "constant",
    "modifier": "keyword",
    "annotation": "keyword",
}
_LITERAL_DOC = {
    "true": "布尔字面量：真。", "false": "布尔字面量：假。",
}

# 结构模板 id → 说明（覆盖 STRUCTURE_TEMPLATES / SNIPPETS / WORKFLOW_BLOCKS 未给出 doc 的）
def _item(cid, label, kind, category, insert_text, doc, detail=None,
          trigger=None, parameters=None, rules=None, placeholders=None):
    item = {
        "id": cid, "label": label, "kind": kind, "category": category,
        "insertText": insert_text, "documentation": doc,
    }
    if detail:
        item["detail"] = detail
    if trigger:
        item["triggerCharacters"] = trigger
    if parameters:
        item["parameters"] = parameters
    if rules:
        item["insertTextRules"] = rules
    if placeholders:
        item["placeholders"] = placeholders
    return item


def _load_sources():
    for path, label in ((GRAMMAR_PATH, "GRAMMAR.pest"), (AST_PATH, "AST.rs")):
        if not os.path.isfile(path):
            raise FileNotFoundError(
                f"CloudFlow 补全生成器错误：找不到 {label} 源文件 {path}。"
            )
    with open(GRAMMAR_PATH, "r", encoding="utf-8") as fh:
        grammar_src = fh.read()
    with open(AST_PATH, "r", encoding="utf-8") as fh:
        ast_src = fh.read()
    return grammar_src, ast_src


def _keyword_items(grammar) -> list:
    """关键字补全项（需求 15.3）：flat 列表 + 结构分组。"""
    items = []
    for cat, words in grammar.get("keywords", {}).items():
        kind = _KEYWORD_KIND.get(cat, "keyword")
        for word in words:
            if word in ("vars", "steps", "workflow"):
                # 引用前缀由 referencePrefixes 专项提供；此处仍以关键字形式提供（输入前缀时引导）。
                kind = "module"
            doc = _LITERAL_DOC.get(word, f"CloudFlow 关键字：{word}（{cat}）。")
            items.append(_item(
                f"kw_{cat}_{word}", word, kind, "keyword", word, doc,
                detail=f"category={cat}", trigger=[" ", "."],
            ))
    return items


def _block_items() -> list:
    """顶层块/声明补全项（需求 15.5/2.x）。"""
    items = []
    for key, blk in cfg.WORKFLOW_BLOCKS.items():
        items.append(_item(
            f"block_{key}", key, "snippet", "block", blk["template"], blk["doc"],
            rules="snippet", trigger=[" "],
        ))
    return items


def _structure_items() -> list:
    """控制流/结构模板补全项（需求 15.6-15.7）。"""
    items = []
    for key, tmpl in cfg.STRUCTURE_TEMPLATES.items():
        items.append(_item(
            f"struct_{key}", key, "snippet", "structure", tmpl["template"],
            tmpl["doc"], rules="snippet", trigger=[" "],
        ))
    return items


def _snippet_items() -> list:
    """常用片段（需求 15.23）：独立分类，与 structure 分开。"""
    snippets = {}
    items = []
    for key, snip in cfg.SNIPPETS.items():
        snippets[key] = {"prefix": snip["prefix"], "template": snip["template"], "doc": snip["doc"]}
        items.append(_item(
            f"snippet_{key}", snip["prefix"], "snippet", "snippet", snip["template"],
            snip["doc"], rules="snippet", trigger=[" "],
        ))
    return items, snippets


def _function_items() -> list:
    """内置函数补全 + 签名帮助参数（需求 15.17）。"""
    items = []
    for name, info in cfg.BUILTIN_FUNCTIONS.items():
        items.append(_item(
            f"func_{name}", name, "function", "function",
            f"{name}(${{1}})", info["doc"],
            detail=info["signature"], trigger=["("],
            parameters=info.get("parameters", []),
        ))
    for name, info in cfg.PIPELINE_OPERATORS.items():
        items.append(_item(
            f"pipe_{name}", name, "function", "pipeline",
            f"{name}(${{1}})", info["doc"], detail=info["signature"], trigger=["("],
        ))
    return items


def _type_items() -> list:
    """类型名补全（需求 15.18）。"""
    return [_item(f"type_{t}", t, "type", "type", t, f"CloudFlow 类型：{t}。",
                  detail="type", trigger=[" "]) for t in cfg.COMPLETION_TYPES if t != "input"] + [
        _item("type_input", "input", "keyword", "type", "input.", "输入声明前缀：input.<type>(...)。",
              detail="modifier", trigger=["."]),
    ]


def _trigger_spec() -> dict:
    """触发器补全与字段提示（需求 15.20）。"""
    trigger_items = []
    for key, t in cfg.TRIGGER_TYPES.items():
        trigger_items.append(_item(
            f"trigger_{key}", t["label"], "snippet", "trigger", t["template"], t["doc"],
            rules="snippet", trigger=[" "],
        ))
    return {
        "types": {k: {kk: vv for kk, vv in v.items() if kk != "template"}
                  for k, v in cfg.TRIGGER_TYPES.items()},
        "items": trigger_items,
        "allTriggerKeywords": sorted(cfg.TRIGGER_TYPES),
    }


def _reference_spec(grammar) -> dict:
    """变量/步骤/工作流引用前缀（需求 15.11-15.12）。"""
    out = {}
    for prefix, info in cfg.COMPLETION_REF_PREFIXES.items():
        out[prefix] = {
            "prefix": f"{prefix}.",
            "doc": info["doc"],
            "nextSegment": info["nextSegment"],
            "scope": grammar.get("references", {}).get(prefix, {}).get("scope", ""),
        }
    return out


def build_completions(grammar_src, ast_src) -> dict:
    grammar = grammar_scraper.scrape(grammar_src)
    ast = ast_scraper.scrape(ast_src)

    keyword_items = _keyword_items(grammar)
    block_items = _block_items()
    structure_items = _structure_items()
    snippet_items, snippets = _snippet_items()
    function_items = _function_items()
    type_items = _type_items()
    trigger_spec = _trigger_spec()

    items = (keyword_items + block_items + structure_items + function_items
             + type_items + trigger_spec["items"] + snippet_items)

    # AST 节点名称快照：供结构模板与代码结构提示对账（需求 15.5）
    ast_nodes = {
        "flowNodeVariants": ast["flowNodeVariants"],
        "structs": ast["types"]["structs"],
        "enums": ast["types"]["enums"],
    }

    # 可用能力/动作补全为运行时动态来源（前端通过 props/API 注入），规范内提供占位说明。
    capability_meta = {
        "dynamic": True,
        "note": "action 名称与参数由 Capability Hub schema 提供；前端在运行时注入 capabilities。",
        "parameterFields": ["node", "path", "filter", "text", "source", "target", "message", "channel", "to"],
    }

    return {
        "$meta": {
            **cfg.LANGUAGE,
            "specVersion": "1.0.0",
            "generatedFor": "completion",
            "generatedFrom": {
                "grammar": "src/grammar.pest",
                "ast": "crates/cloudflow-engine-core/src/ast.rs",
                "grammarDigest": _file_digest(GRAMMAR_PATH),
                "astDigest": _file_digest(AST_PATH),
            },
            "generatedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
            "counts": {
                "keywords": len(keyword_items),
                "blocks": len(block_items),
                "structures": len(structure_items),
                "functions": len(function_items),
                "types": len(type_items),
                "triggers": len(trigger_spec["items"]),
                "snippets": len(snippet_items),
            },
        },
        "keywords": keyword_items,
        "topLevelKeywords": sorted(set(
            [w for cat in ("control", "declaration") for w in grammar.get("keywords", {}).get(cat, [])]
        )),
        "blocks": block_items,
        "structureTemplates": structure_items,
        "builtinFunctions": [
            {"name": n, **i} for n, i in cfg.BUILTIN_FUNCTIONS.items()
        ],
        "pipelineOperators": list(cfg.PIPELINE_OPERATORS),
        "types": type_items,
        "triggers": trigger_spec,
        "retryExceptions": cfg.RETRY_EXCEPTIONS,
        "referencePrefixes": _reference_spec(grammar),
        "capabilities": capability_meta,
        "errorCodes": cfg.ERROR_CODES,
        "pairs": cfg.PAIR_RULES,
        "snippets": snippets,
        "astNodes": ast_nodes,
        "items": items,
    }


def _file_digest(path: str) -> str:
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def validate_spec(spec: dict) -> list:
    errors = []
    if not spec["$meta"].get("generatedFor") == "completion":
        errors.append("$meta.generatedFor 应为 completion")
    try:
        import jsonschema
        with open(SCHEMA_PATH, "r", encoding="utf-8") as fh:
            schema = json.load(fh)
        jsonschema.validate(spec, schema)
    except ImportError:
        for key in ("$meta", "keywords", "structureTemplates", "builtinFunctions",
                    "types", "triggers", "retryExceptions", "blocks", "pairs",
                    "items", "errorCodes", "snippets", "referencePrefixes"):
            if key not in spec:
                errors.append(f"缺少顶层键: {key}")
    except Exception as exc:  # noqa: BLE001
        errors.append(f"JSON Schema 校验失败: {exc}")
    return errors


def run(force: bool = False, verbose: bool = False) -> int:
    grammar_src, ast_src = _load_sources()
    gram_digest = _file_digest(GRAMMAR_PATH)
    ast_digest = _file_digest(AST_PATH)

    if os.path.isfile(COMPLETION_OUT) and not force:
        try:
            with open(COMPLETION_OUT, "r", encoding="utf-8") as fh:
                old = json.load(fh)
            gf = old.get("$meta", {}).get("generatedFrom", {})
            if gf.get("grammarDigest") == gram_digest and gf.get("astDigest") == ast_digest:
                if verbose:
                    print("[completion] 产物已是最新，跳过（使用 --force 强制重生成）")
                return 0
        except Exception:  # noqa: BLE001
            pass

    spec = build_completions(grammar_src, ast_src)
    errors = validate_spec(spec)
    if errors:
        print("[completion] 错误：补全规范未通过校验", file=sys.stderr)
        for e in errors:
            print("  - " + e, file=sys.stderr)
        return 1

    os.makedirs(OUT_DIR, exist_ok=True)
    with open(COMPLETION_OUT, "w", encoding="utf-8") as fh:
        fh.write(json.dumps(spec, ensure_ascii=False, indent=2) + "\n")

    if verbose:
        c = spec["$meta"]["counts"]
        print(f"[completion] 已生成 {os.path.relpath(COMPLETION_OUT, HERE)} | "
              f"关键字 {c['keywords']} | 块 {c['blocks']} | 结构 {c['structures']} | "
              f"函数 {c['functions']} | 类型 {c['types']} | 触发器 {c['triggers']} | 片段 {c['snippets']}")
    return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="CloudFlow 统一代码补全规范生成器")
    parser.add_argument("--force", action="store_true", help="忽略增量检查，全量重新生成")
    parser.add_argument("--verbose", action="store_true", help="打印统计日志")
    args = parser.parse_args(argv)
    try:
        return run(force=args.force, verbose=args.verbose)
    except FileNotFoundError as exc:
        print(f"[completion] 错误: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
