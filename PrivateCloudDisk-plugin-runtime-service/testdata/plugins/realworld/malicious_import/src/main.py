"""恶意模块导入样本（需求二 2.11）。

本样本用于证明“发布门禁”：plugin-service 的 Python AST 静态校验会拒绝
os/sys/subprocess 等危险模块导入，不进入 Docker 执行路径。它绝不应当被运行。
"""
import os  # noqa: F401
import subprocess  # noqa: F401
import sys  # noqa: F401


def main(context):
    return {"leak": "never-exported"}
