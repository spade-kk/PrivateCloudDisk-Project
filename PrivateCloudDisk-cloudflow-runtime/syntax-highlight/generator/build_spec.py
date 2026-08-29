#!/usr/bin/env python3
"""CloudFlow 统一语法高亮规范生成器（统一解析脚本，需求 14.x）。

职责（需求 14.1-14.6）：读取 GRAMMAR.pest 与 AST.rs 源码文本，提取全部语法元素：
- 关键字、操作符、字面量、注释、字符串、数字、标识符、变量引用（来自 GRAMMAR.pest）
- AST 节点名、结构体/枚举、FlowNode 变体及其 meta scope（来自 AST.rs）
产出唯一事实来源 `cloudflow.syntax-highlight.json`，供 TextMate/Monarch/HLJS 三个转换器消费。

支持：
- `--force`：全量重新生成（需求 14.13）；缺省增量（若产物已存在且源文件未变更则跳过）。
- `--watch`：监听 GRAMMAR.pest / AST.rs 变更自动重新生成（需求 14.28）。
- `--json-schema`：加载 schema/*.schema.json 校验产物（需求 8.11/14.21、测试 12.1）。
- `--verbose`：打印提取统计日志（需求 14.23）。
- 错误处理（需求 14.22）：源文件缺失/损坏时输出清晰错误并以非零码退出，不生成损坏产物。

用法：
    python3 syntax-highlight/generator/build_spec.py [--force] [--watch] [--verbose]
"""
from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ast_scraper  # noqa: E402
import config as cfg  # noqa: E402
import grammar_scraper  # noqa: E402

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GRAMMAR_PATH = os.path.join(REPO_ROOT, "src", "grammar.pest")
AST_PATH = os.path.join(REPO_ROOT, "src", "ast.rs")
OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build")
SPEC_OUT = os.path.join(OUT_DIR, "cloudflow.syntax-highlight.json")
SCHEMA_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "schema")


def _file_digest(path: str) -> str:
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def load_sources() -> tuple:
    """读取语法与 AST 源码文本；缺失时抛清晰错误（需求 14.22）。"""
    for path, label in ((GRAMMAR_PATH, "GRAMMAR.pest"), (AST_PATH, "AST.rs")):
        if not os.path.isfile(path):
            raise FileNotFoundError(
                f"CloudFlow 语法高亮生成器错误：找不到 {label} 源文件 {path}。"
                f"（需求 14.22：源文件缺失时不生成损坏产物）"
            )
    with open(GRAMMAR_PATH, "r", encoding="utf-8") as fh:
        grammar_src = fh.read()
    with open(AST_PATH, "r", encoding="utf-8") as fh:
        ast_src = fh.read()
    return grammar_src, ast_src


def build_spec(grammar_src: str, ast_src: str) -> dict:
    """组装统一规范：合并 config / grammar 提取 / AST 提取 / 元信息。"""
    grammar = grammar_scraper.scrape(grammar_src)
    ast = ast_scraper.scrape(ast_src)

    def count(cat: str) -> int:
        items = grammar.get(cat, {})
        if isinstance(items, dict):
            return sum(len(v) for v in items.values()) if cat in ("keywords", "operators") else len(items)
        return len(items)

    spec = {
        "$meta": {
            **cfg.LANGUAGE,
            "blockComment": None,
            "generatedFrom": {
                "grammar": "src/grammar.pest",
                "ast": "src/ast.rs",
            },
            "generatedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
            "sourceTokens": {
                "keywords": sum(len(v) for v in grammar["keywords"].values()),
                "operators": sum(len(v) for v in grammar["operators"].values()),
                "punctation": len(grammar["punctation"]),
                "unclassifiedTokens": len(grammar["unclassifiedTokens"]),
                "astStructs": len(ast["types"]["structs"]),
                "astEnums": len(ast["types"]["enums"]),
                "flowNodeVariants": len(ast["flowNodeVariants"]),
            },
        },
        "categories": cfg.TOKEN_CATEGORIES,
        "keywords": grammar["keywords"],
        "operators": grammar["operators"],
        "punctation": grammar["punctation"],
        "comments": grammar["comments"],
        "strings": grammar["strings"],
        "numbers": grammar["numbers"],
        "references": grammar["references"],
        "identifiers": grammar["identifiers"],
        "ast": ast,
        "unclassifiedTokens": grammar["unclassifiedTokens"],
    }
    return spec


def validate_spec(spec: dict) -> list:
    """校验产物是否满足 schema（需求 8.11 / 测试 12.1）。若无 jsonschema 则做最小必填校验。"""
    errors: list = []
    try:
        import jsonschema
    except ImportError:
        # 兜底：校验必填顶层键（需求 14.30 声称纯 stdlib，CI 可无 jsonschema）
        required = ["$meta", "categories", "keywords", "operators", "punctation",
                    "comments", "strings", "numbers", "references", "identifiers",
                    "ast", "unclassifiedTokens"]
        for key in required:
            if key not in spec:
                errors.append(f"缺少顶层键: {key}")
        return errors

    schema_path = os.path.join(SCHEMA_DIR, "cloudflow.syntax-highlight.schema.json")
    try:
        with open(schema_path, "r", encoding="utf-8") as fh:
            schema = json.load(fh)
        jsonschema.validate(spec, schema)
    except Exception as exc:  # noqa: BLE001
        errors.append(f"JSON Schema 校验失败: {exc}")
    return errors


def write_spec(spec: dict, verbose: bool = False) -> str:
    os.makedirs(OUT_DIR, exist_ok=True)
    text = json.dumps(spec, ensure_ascii=False, indent=2) + "\n"
    with open(SPEC_OUT, "w", encoding="utf-8") as fh:
        fh.write(text)
    if verbose:
        print(f"[build_spec] 已生成 {SPEC_OUT}")
    return SPEC_OUT


def run(force: bool = False, verbose: bool = False) -> int:
    grammar_src, ast_src = load_sources()
    gram_digest = _file_digest(GRAMMAR_PATH)
    ast_digest = _file_digest(AST_PATH)

    # 增量：产物存在且源未变更则跳过（需求 14.13）
    if os.path.isfile(SPEC_OUT) and not force:
        try:
            with open(SPEC_OUT, "r", encoding="utf-8") as fh:
                old = json.load(fh)
            if (old.get("$meta", {}).get("generatedFrom", {}).get("grammarDigest") == gram_digest
                    and old.get("$meta", {}).get("generatedFrom", {}).get("astDigest") == ast_digest):
                if verbose:
                    print("[build_spec] 产物已是最新，跳过（使用 --force 强制重生成）")
                return 0
        except Exception:  # noqa: BLE001
            pass

    spec = build_spec(grammar_src, ast_src)
    spec["$meta"]["generatedFrom"]["grammarDigest"] = gram_digest
    spec["$meta"]["generatedFrom"]["astDigest"] = ast_digest

    errors = validate_spec(spec)
    if errors:
        print("[build_spec] 错误：规范未通过校验", file=sys.stderr)
        for e in errors:
            print("  - " + e, file=sys.stderr)
        return 1

    write_spec(spec, verbose=verbose)

    if verbose:
        st = spec["$meta"]["sourceTokens"]
        print(f"[build_spec] 关键字 {st['keywords']} | 操作符 {st['operators']} | "
              f"标点 {st['punctation']} | 未分类 {st['unclassifiedTokens']} | "
              f"AST structs {st['astStructs']} | AST enums {st['astEnums']} | "
              f"FlowNode 变体 {st['flowNodeVariants']}")
        if spec["unclassifiedTokens"]:
            print("[build_spec] 警告：存在未分类 token（需求 3.7/3.8，需人工标注）: "
                  + ", ".join(spec["unclassifiedTokens"]))
    return 0


def watch(verbose: bool = False) -> int:
    """--watch 模式：轮询源文件变更自动重生成（需求 14.28）。"""
    print("[build_spec] --watch 模式：监听 GRAMMAR.pest / AST.rs，Ctrl+C 退出")
    last = (_file_digest(GRAMMAR_PATH), _file_digest(AST_PATH))
    try:
        while True:
            try:
                cur = (_file_digest(GRAMMAR_PATH), _file_digest(AST_PATH))
            except FileNotFoundError:
                time.sleep(1.0)
                continue
            if cur != last:
                print("[build_spec] 检测到源文件变更，重新生成...")
                run(force=True, verbose=verbose)
                last = cur
            time.sleep(1.0)
    except KeyboardInterrupt:
        print("\n[build_spec] 已停止 watch")
        return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="CloudFlow 统一语法高亮规范生成器")
    parser.add_argument("--force", action="store_true", help="忽略增量检查，全量重新生成")
    parser.add_argument("--watch", action="store_true", help="监听源文件变更自动重生成")
    parser.add_argument("--verbose", action="store_true", help="打印提取统计与告警日志")
    args = parser.parse_args(argv)

    try:
        if args.watch:
            return watch(verbose=args.verbose)
        return run(force=args.force, verbose=args.verbose)
    except FileNotFoundError as exc:
        print(f"[build_spec] 错误: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
