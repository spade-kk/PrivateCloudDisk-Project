"""沙箱内插件入口加载器；仅负责 SDK 配置、入口调用和结构化结果落盘。"""

from __future__ import annotations

import importlib.util
import json
import os
import sys
import traceback

sys.path.insert(0, "/opt/pcd-sdk")
import pycloud

RESULT_PATH = "/workspace/work/result.json"


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


def main() -> None:
    module_path = os.environ["PCD_MODULE_PATH"]
    function_name = os.environ["PCD_FUNCTION_NAME"]
    context_path = os.environ["PCD_CONTEXT_PATH"]
    if not module_path.startswith("/workspace/plugin/"):
        raise RuntimeError("入口脚本路径越界")
    with open(context_path, "r", encoding="utf-8") as stream:
        context = json.load(stream)
    pycloud.configure(context)

    spec = importlib.util.spec_from_file_location("pcd_user_plugin", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError("无法加载插件入口模块")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    function = getattr(module, function_name, None)
    if not callable(function):
        raise RuntimeError("插件入口函数不存在或不可调用")
    returned = function(pycloud.current_context())
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
