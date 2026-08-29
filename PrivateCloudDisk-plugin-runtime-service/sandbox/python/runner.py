"""沙箱内插件入口加载器；仅负责 SDK 配置、受限执行与结构化结果落盘。

所有云插件都经过本启动器执行（禁止直接执行用户代码，36.4）。默认走
restricted.exec_plugin 受限执行层（白名单 import + 危险内置删除 + AST 逃逸链
改写 + PEP 578 审计钩子，36.5-36.24）；仅测试探针（验证 Docker 层隔离的手写
fixture，非业务插件）可通过 PCD_RESTRICTED_PYTHON=0 关闭受限层。
"""

from __future__ import annotations

import importlib.util
import argparse
import json
import os
import sys

sys.path.insert(0, "/opt/pcd-sdk")
import pycloud
import restricted

RESULT_PATH = "/workspace/work/result.json"

# 受限层开关：默认开启。显式 "0"/"false" 时退回 importlib 直接加载，仅供探针夹具。
_RESTRICTED = os.environ.get("PCD_RESTRICTED_PYTHON", "1").lower() not in {"0", "false", "no"}


def write_result(
    success: bool,
    modified: bool,
    error: str = "",
    output: object | None = None,
) -> None:
    temporary = RESULT_PATH + ".tmp"
    with open(temporary, "x", encoding="utf-8") as stream:
        json.dump(
            {
                "success": success,
                "modified": modified,
                "error": error[:1000],
                # 工作流能力函数通过结构化 JSON 返回值，不允许返回文件句柄或宿主对象。
                "output": output,
            },
            stream,
            ensure_ascii=False,
        )
        stream.flush()
    os.replace(temporary, RESULT_PATH)


def _load_legacy(module_path: str, function_name: str, context):
    """探针模式：importlib 直接加载（仅 PCD_RESTRICTED_PYTHON=0 时使用）。"""
    spec = importlib.util.spec_from_file_location("pcd_user_plugin", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError("无法加载插件入口模块")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    function = getattr(module, function_name, None)
    if not callable(function):
        raise RuntimeError("插件入口函数不存在或不可调用")
    return function(context)


def _run_entry(module_path: str, function_name: str, context):
    """执行插件入口：受限层优先，探针模式回退 importlib。"""
    if _RESTRICTED:
        with open(module_path, "r", encoding="utf-8") as stream:
            source = stream.read()
        return restricted.exec_plugin(
            source,
            module_path,
            function_name,
            context,
            pycloud_module=pycloud,
        )
    return _load_legacy(module_path, function_name, context)


def main() -> None:
    # [CF-PLUGIN-UDS-001] Instance token comes only from Runtime-owned process
    # argv. Do not move it to environment variables or context.json: both have a
    # wider accidental-observability surface inside the container.
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--pcd-instance-id", required=True)
    parser.add_argument("--pcd-instance-token", required=True)
    args = parser.parse_args()
    module_path = os.environ["PCD_MODULE_PATH"]
    function_name = os.environ["PCD_FUNCTION_NAME"]
    context_path = os.environ["PCD_CONTEXT_PATH"]
    if not module_path.startswith("/workspace/plugin/"):
        raise RuntimeError("入口脚本路径越界")
    with open(context_path, "r", encoding="utf-8") as stream:
        context = json.load(stream)
    # `_configure_runtime_transport` is intentionally private: only this
    # trusted bootstrap may receive the argv credential; user plugin AST is
    # blocked from reaching private PyCloud transport attributes.
    pycloud._configure_runtime_transport(args.pcd_instance_id, args.pcd_instance_token)
    pycloud.configure(context)

    returned = _run_entry(module_path, function_name, context)
    output = None
    if isinstance(returned, (bytes, bytearray, memoryview)):
        pycloud.write(returned)
    elif returned is not None:
        # json.dumps 会拒绝不可序列化对象，统一进入下方安全异常边界。
        json.dumps(returned, ensure_ascii=False)
        output = returned
    modified = os.path.isfile("/workspace/work/output.bin")
    write_result(True, modified, output=output)


if __name__ == "__main__":
    try:
        main()
    except BaseException as exception:
        # 不输出宿主内部堆栈；用户错误只保留类型和短消息。
        write_result(
            False,
            False,
            f"{type(exception).__name__}: {str(exception)[:800]}",
        )
        print(
            f"plugin_error={type(exception).__name__}: {str(exception)[:800]}",
            file=sys.stderr,
        )
        sys.exit(1)
