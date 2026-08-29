#!/usr/bin/env python3
"""CloudFlow 统一规范 → 多平台语法文件转换工具（需求 8.x / 14.15）。

用法：
    python3 syntax-highlight/generator/convert.py --format tmLanguage   # 默认
    python3 syntax-highlight/generator/convert.py --format monarch
    python3 syntax-highlight/generator/convert.py --format hljs
    python3 syntax-highlight/generator/convert.py --format all         # 依次生成三种
    python3 syntax-highlight/generator/convert.py --force               # 强制重读规范并转换

输出（固定文件名，需求 8.7）：
    build/cloudflow.tmLanguage.json    —— VS Code TextMate
    build/cloudflow.monarch.json/.ts   —— Monaco Monarch
    build/cloudflow.hljs.js            —— Highlight.js（UMD，ESM+CJS）

转换器接口（需求 14.15）：每个模块实现 `generate(spec, options=None) -> str`。
新增格式（Prism.js/CodeMirror，需求 8.13/14.14）只需在此表登记新模块。
"""
from __future__ import annotations

import argparse
import importlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CONVERTERS = os.path.join(HERE, "converters")
BUILD = os.path.join(HERE, "..", "build")
SPEC = os.path.join(BUILD, "cloudflow.syntax-highlight.json")

# 输出格式登记表：名称 -> (转换器模块名, [输出文件名], 附加 options)
FORMATS = {
    "tmLanguage": ("textmate", ["cloudflow.tmLanguage.json"], {}),
    "monarch": ("monarch", ["cloudflow.monarch.json", "cloudflow.monarch.ts"], {"ts": True}),
    "hljs": ("hljs", ["cloudflow.hljs.js"], {}),
}


def _load_spec() -> dict:
    if not os.path.isfile(SPEC):
        print(f"[convert] 错误：未找到统一规范 {SPEC}。请先运行 build_spec.py。", file=sys.stderr)
        sys.exit(1)
    with open(SPEC, "r", encoding="utf-8") as fh:
        return json.load(fh)


def _monarch_ts(source_json: str) -> str:
    """把 Monarch JSON 转成 TypeScript 默认导出对象（需求 6.15）。"""
    return ("// 自动生成：由 cloudflow.syntax-highlight.json 经 convert.py --format monarch 生成，请勿手动修改。\n"
            "// 用法：import { cloudflowMonarch } from './cloudflow.monarch';\n"
            "//       monaco.languages.setMonarchTokensProvider('cloudflow', cloudflowMonarch);\n"
            "export const cloudflowMonarch: Record<string, any> = "
            + source_json + ";\n")


def convert(fmt: str, force: bool = False, verbose: bool = False) -> None:
    sys.path.insert(0, CONVERTERS)
    spec = _load_spec()

    if fmt == "all":
        for f in FORMATS:
            convert(f, force=force, verbose=verbose)
        return

    if fmt not in FORMATS:
        print(f"[convert] 未知格式 {fmt}，可选: {list(FORMATS)} / all", file=sys.stderr)
        sys.exit(2)

    mod_name, outputs, opts = FORMATS[fmt]
    module = importlib.import_module(mod_name)
    text = module.generate(spec, dict(opts))

    os.makedirs(BUILD, exist_ok=True)
    for out_name in outputs:
        out_path = os.path.join(BUILD, out_name)
        payload = _monarch_ts(text) if out_name.endswith(".ts") else text
        with open(out_path, "w", encoding="utf-8") as fh:
            fh.write(payload)
        if verbose:
            print(f"[convert] 已生成 {os.path.relpath(out_path, HERE)} ({len(payload)} bytes)")
    # 非扩展检查：to stdout 校验 JSON 可解析
    if fmt == "tmLanguage":
        json.loads(text)
    print(f"[convert] ok: {fmt}")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="CloudFlow 统一规范 → 语法文件转换工具")
    parser.add_argument("--format", choices=list(FORMATS) + ["all"], default="all",
                        help="输出格式（默认 all）")
    parser.add_argument("--force", action="store_true", help="强制读取规范并转换")
    parser.add_argument("--verbose", action="store_true", help="打印每个输出文件")
    args = parser.parse_args(argv)
    convert(args.format, force=args.force, verbose=args.verbose)
    return 0


if __name__ == "__main__":
    sys.exit(main())
