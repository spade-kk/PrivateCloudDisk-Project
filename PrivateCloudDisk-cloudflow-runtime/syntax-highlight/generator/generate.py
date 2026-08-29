#!/usr/bin/env python3
"""CloudFlow 统一生成器：一键生成语法高亮 + 代码补全全部产物（需求 14.x / 15.25）。

按序执行：
  1) build_spec.py --force       —— 语法高亮规范 cloudflow.syntax-highlight.json
  2) completion_builder.py --force —— 代码补全规范 cloudflow.completion.json
  3) convert.py --format all     —— 转换 3 个平台（TextMate / Monarch / HLJS）
  4) completion_convert.py --web —— VS Code 片段 + Web 分发

用法：
    python3 syntax-highlight/generator/generate.py [--web <web-languages-dir>] [--verbose]
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
WEB_DEFAULT = os.path.normpath(
    os.path.join(HERE, "..", "..", "..", "PrivateCloudDisk-web", "src", "languages")
)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="CloudFlow 统一生成器")
    parser.add_argument("--web", default=WEB_DEFAULT, help="Web 前端 languages 目录")
    parser.add_argument("--no-web", action="store_true", help="跳过 Web 分发拷贝")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args(argv)

    py = sys.executable
    steps = [
        [py, os.path.join(HERE, "build_spec.py"), "--force"],
        [py, os.path.join(HERE, "completion_builder.py"), "--force"],
        [py, os.path.join(HERE, "convert.py"), "--format", "all", "--force"],
    ]
    if not args.no_web:
        steps.append([py, os.path.join(HERE, "completion_convert.py"),
                      "--web", args.web] + (["--verbose"] if args.verbose else []))

    code = 0
    for step in steps:
        if args.verbose:
            print(f"[generate] $ {' '.join(step)}")
        result = subprocess.run(step, cwd=HERE)
        if result.returncode != 0:
            print(f"[generate] 步骤失败: {' '.join(step)}", file=sys.stderr)
            code = 1
            break
    if code == 0 and args.verbose:
        print("[generate] 所有产物生成完成")
    return code


if __name__ == "__main__":
    sys.exit(main())
