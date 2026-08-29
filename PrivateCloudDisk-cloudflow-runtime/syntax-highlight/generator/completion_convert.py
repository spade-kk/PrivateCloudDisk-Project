#!/usr/bin/env python3
"""CloudFlow 补全规范 → VS Code 片段 + 分发拷贝转换工具（需求 15.26）。

读取 build/cloudflow.completion.json：
- 生成 vscode/snippets/cloudflow.code-snippets（VS Code snippets 贡献）；
- 把 cloudflow.completion.json 拷贝到 vscode/syntaxes/ 与（可选）Web 前端 src/languages/，
  使 VS Code 完成提供器与 Monaco 前端都能引用同一唯一事实来源。

用法：
    python3 syntax-highlight/generator/completion_convert.py [--web <web-languages-dir>] [--force] [--verbose]
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(HERE, "..", "build")
VSCODE = os.path.join(HERE, "..", "vscode")
VSCODE_SNIPPETS = os.path.join(VSCODE, "snippets")
COMPLETION_JSON = os.path.join(BUILD, "cloudflow.completion.json")
VSCODE_COPY = os.path.join(VSCODE, "syntaxes", "cloudflow.completion.json")


def _load() -> dict:
    if not os.path.isfile(COMPLETION_JSON):
        print(f"[completion_convert] 错误：未找到 {COMPLETION_JSON}。请先运行 completion_builder.py。",
              file=sys.stderr)
        sys.exit(1)
    with open(COMPLETION_JSON, "r", encoding="utf-8") as fh:
        return json.load(fh)


def _body(template: str) -> list:
    """把带 ${1:...} placeholders 的模板折叠成 VS Code snippet body 行数组。"""
    if "\n" not in template:
        return [template]
    lines = template.split("\n")
    return lines


def _vscode_snippets(spec: dict) -> dict:
    snippets = {}
    for key, snip in spec["snippets"].items():
        snippets[snip["prefix"].title()] = {
            "prefix": snip["prefix"],
            "body": _body(snip["template"]),
            "description": snip["doc"],
        }
    # 结构模板也作为片段导出（控制流），避免与 snippet 重复时覆盖同名前缀：
    # 结构模板优先级更高，先写结构，后写专用片段（后写者同 prefix 覆盖）。
    for item in spec.get("structureTemplates", []):
        snippets[f"struct_{item['id']}"] = {
            "prefix": item["label"],
            "body": _body(item["insertText"]),
            "description": item["documentation"],
        }
    for item in spec.get("blocks", []):
        snippets[f"block_{item['id']}"] = {
            "prefix": item["label"],
            "body": _body(item["insertText"]),
            "description": item["documentation"],
        }
    return snippets


def run(web_dir, force: bool = False, verbose: bool = False) -> int:
    spec = _load()
    os.makedirs(VSCODE_SNIPPETS, exist_ok=True)
    os.makedirs(os.path.join(VSCODE, "syntaxes"), exist_ok=True)
    out = os.path.join(VSCODE_SNIPPETS, "cloudflow.code-snippets")
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(_vscode_snippets(spec), fh, ensure_ascii=False, indent=2)
    if verbose:
        print(f"[completion_convert] 已生成 {os.path.relpath(out, HERE)}")

    shutil.copyfile(COMPLETION_JSON, VSCODE_COPY)
    if verbose:
        print(f"[completion_convert] 已拷贝 {os.path.relpath(VSCODE_COPY, HERE)}")

    if web_dir:
        os.makedirs(web_dir, exist_ok=True)
        dst = os.path.join(web_dir, "cloudflow.completion.json")
        shutil.copyfile(COMPLETION_JSON, dst)
        if verbose:
            print(f"[completion_convert] 已拷贝 {dst}")
    return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="CloudFlow 补全规范 → VS Code/Web 分发")
    parser.add_argument("--web", help="Web 前端 languages 目录（拷贝 cloudflow.completion.json）")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args(argv)
    return run(args.web, force=args.force, verbose=args.verbose)


if __name__ == "__main__":
    sys.exit(main())
