#!/usr/bin/env python3
"""真实场景插件基线生成工具（需求三 3.18/3.19）。

在宿主侧离线模拟沙箱上下文（pycloud 路径打补丁 + 内存中的能力网关 mock），
执行真实插件源码并冻结测试基线到 testdata/expected/*.golden。

不是 AST 发布门禁的一部分，允许使用任意标准库；生成结果必须与 Docker 集成
测试输出逐字节一致（沙箱内 pycloud.write 只是写 work/output.bin）。

用法：
    python3 scripts/gen_baselines.py            # 全部
    python3 scripts/gen_baselines.py text_stats # 单个
"""
from __future__ import annotations

import importlib.util
import json
import os
import shutil
import socket
import sys
import tempfile
import threading

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RWE_ROOT = os.path.join(ROOT, "testdata", "plugins", "realworld")
EXPECTED_DIR = os.path.join(ROOT, "testdata", "expected")
SDK_DIR = os.path.join(ROOT, "sandbox", "python")
sys.path.insert(0, SDK_DIR)

import pycloud  # noqa: E402
from pycloud import file as pc_file  # noqa: E402
from pycloud import capabilities  # noqa: E402

# 每个插件的基线采集模式。
#   output-bin : 收集沙箱 work/output.bin（Execute 修改链）
#   return-json: 收集主函数结构化返回（ExecuteCapability 链）
#   chain      : 多入口链，每步输出作为下一步输入
MODE = {
    "text_stats": "output-bin",
    "json_cleaner": "output-bin",
    "csv_report": "output-bin",
    "excel_generate": "output-bin",
    "excel_parse": "output-bin",
    "capability_report": "return-json",
    "capability_user_info": "return-json",
    "path_escape": "return-json",
    "content_reverse": "output-bin",
    "multi_entry_pkg": "chain",
    # 红色/异常样本不生成基线：timeout_sim, resource_hog, malicious_import, invalid_output
}

# 能力网关 mock：key -> (ok, output 或 error_code/message)
# 与 Go 集成测试 capabilityRelay 保持一致；数据面行为即“权限守卫”。
CAPABILITY_STUB = {
    "api.file.generate_excel": {
        "ok": True,
        "output": {
            "content_type": "text/csv",
            "content": "product,amount,price\ndisk,3,99.5\nssd,2,299.0\n",
        },
    },
    "api.user.info": {
        "ok": True,
        "output": {
            "user_id": "user-1",
            "nickname": "u***r",
            "email": "***@example.com",
        },
    },
    "api.file.content.get": {"ok": False, "error_code": "CAPABILITY_FORBIDDEN",
                             "message": "路径不在可访问白名单"},
}


def _read_exact(connection, size):
    chunks = []
    while size:
        data = connection.recv(size)
        if not data:
            raise OSError("socket closed")
        chunks.append(data)
        size -= len(data)
    return b"".join(chunks)


def _socket_response(request, stub):
    """Build the formal capability_socket.proto wire response for a fixture."""
    response = capabilities._field_bytes(1, request[1])
    if stub["ok"]:
        return response + capabilities._field_bytes(2, b"SUCCESS") + capabilities._field_bytes(
            3, json.dumps(stub["output"], ensure_ascii=False).encode("utf-8")
        )
    error = capabilities._field_bytes(1, stub["error_code"].encode("utf-8")) + capabilities._field_bytes(
        2, stub["message"].encode("utf-8")
    )
    return response + capabilities._field_bytes(2, b"FAILED") + capabilities._field_bytes(4, error)


def _socket_relay(socket_path, stop_event, ready_event):
    """UDS mock Agent used only for offline golden generation.

    It follows the same protobuf frame contract as Runtime Agent and never
    creates request/response files, so generated baselines exercise the SDK's
    new transport semantics rather than the removed polling mechanism.
    """
    listener = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    listener.bind(socket_path)
    listener.listen(16)
    listener.settimeout(0.1)
    ready_event.set()
    try:
        while not stop_event.is_set():
            try:
                connection, _ = listener.accept()
            except TimeoutError:
                continue
            with connection:
                connection.settimeout(0.2)
                while not stop_event.is_set():
                    try:
                        size = int.from_bytes(_read_exact(connection, 4), "big")
                        request = capabilities._decode_fields(_read_exact(connection, size))
                    except (OSError, ValueError):
                        break
                    key = capabilities._field_text(request, 2)
                    stub = CAPABILITY_STUB.get(
                        key, {"ok": False, "error_code": "CAPABILITY_UNKNOWN", "message": "能力未在测试网关注册"}
                    )
                    response = _socket_response(request, stub)
                    connection.sendall(len(response).to_bytes(4, "big") + response)
    finally:
        listener.close()


def _strip_yaml_comment(raw):
    """移除不在引号内的 '#' 注释，返回去除注释后的行。"""
    in_s = in_d = False
    for i, ch in enumerate(raw):
        if ch == '"' and not in_s:
            in_d = not in_d
        elif ch == "'" and not in_d:
            in_s = not in_s
        elif ch == "#" and not in_s and not in_d:
            return raw[:i]
    return raw


def _yaml_scalar(text):
    text = text.strip()
    if text == "" or text == "~":
        return None
    if len(text) >= 2 and text[0] == text[-1] and text[0] in "\"'":
        return text[1:-1]
    low = text.lower()
    if low in ("true", "yes"):
        return True
    if low in ("false", "no"):
        return False
    if low == "null":
        return None
    try:
        return int(text)
    except ValueError:
        pass
    try:
        return float(text)
    except ValueError:
        pass
    return text


def _yaml_map(lines, idx, indent):
    result = {}
    while idx < len(lines):
        cur_indent, text = lines[idx]
        if cur_indent < indent:
            break
        if cur_indent != indent:
            raise ValueError(f"YAML 缩进异常: '{text}'")
        if text.startswith("- "):
            break
        if ":" not in text:
            raise ValueError(f"YAML 期望 'key: value': '{text}'")
        key, _, rest = text.partition(":")
        key = key.strip()
        rest = rest.strip()
        if rest:
            result[key] = _yaml_scalar(rest)
            idx += 1
        elif (idx + 1 < len(lines) and lines[idx + 1][0] >= indent
              and lines[idx + 1][1].startswith("- ")):
            # YAML 允许块序列与 key 同缩进：events: 与 - event: 同级。
            child_indent = lines[idx + 1][0]
            child, idx = _yaml_block(lines, idx + 1, child_indent)
            result[key] = child
        elif idx + 1 < len(lines) and lines[idx + 1][0] > indent:
            child_indent = lines[idx + 1][0]
            child, idx = _yaml_block(lines, idx + 1, child_indent)
            result[key] = child
        else:
            result[key] = None
            idx += 1
    return result, idx


def _yaml_list(lines, idx, indent):
    result = []
    while idx < len(lines):
        cur_indent, text = lines[idx]
        if cur_indent < indent:
            break
        if cur_indent != indent:
            raise ValueError(f"YAML 缩进异常: '{text}'")
        if not text.startswith("- "):
            break
        item_text = text[2:].strip()
        if not item_text:
            if idx + 1 < len(lines) and lines[idx + 1][0] > indent:
                child_indent = lines[idx + 1][0]
                child, idx = _yaml_block(lines, idx + 1, child_indent)
                result.append(child)
            else:
                result.append(None)
                idx += 1
            continue
        if ":" not in item_text:
            result.append(_yaml_scalar(item_text))
            idx += 1
            continue
        key, _, rest = item_text.partition(":")
        key = key.strip()
        item = {}
        if rest.strip():
            item[key] = _yaml_scalar(rest)
            idx += 1
        elif idx + 1 < len(lines) and lines[idx + 1][0] > indent:
            child_indent = lines[idx + 1][0]
            child, idx = _yaml_block(lines, idx + 1, child_indent)
            item[key] = child
        else:
            item[key] = None
            idx += 1
        if idx < len(lines) and lines[idx][0] > indent:
            continuation_indent = lines[idx][0]
            more, idx = _yaml_map(lines, idx, continuation_indent)
            item.update(more)
        result.append(item)
    return result, idx


def _yaml_block(lines, idx, indent):
    if idx >= len(lines):
        return None, idx
    text = lines[idx][1]
    if text.startswith("- "):
        return _yaml_list(lines, idx, indent)
    return _yaml_map(lines, idx, indent)


def load_yaml(text):
    """受限 YAML 子集解析器：仅供我方受控 plugin-runtime manifest 使用。"""
    lines = []
    for raw in text.splitlines():
        line = _strip_yaml_comment(raw)
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip(" "))
        lines.append((indent, line.strip()))
    if not lines:
        return {}
    value, _ = _yaml_block(lines, 0, lines[0][0])
    return value or {}


def _load_manifest(plugin):
    path = os.path.join(RWE_ROOT, plugin, "manifest.yaml")
    with open(path, encoding="utf-8") as stream:
        return load_yaml(stream.read())


def _event_entries(manifest):
    """从 manifest.entrypoints.events 提取 (module, function)，按 priority 升序。"""
    events = ((manifest or {}).get("entrypoints") or {}).get("events") or []
    entries = []
    for event in events:
        module = (event or {}).get("module")
        if not module:
            continue
        entries.append((str(module), str((event or {}).get("function") or "main"),
                        int((event or {}).get("priority") or 0)))
    entries.sort(key=lambda item: item[2])
    return entries


def _export_entry(manifest, name):
    """从 manifest.exports 精确匹配能力，返回 (module, function) 或 None。"""
    for export in (manifest or {}).get("exports") or []:
        if (export or {}).get("name") == name:
            return (str((export or {}).get("module") or "src/main.py"),
                    str((export or {}).get("function") or "main"))
    return None


def _load_module(path):
    spec = importlib.util.spec_from_file_location("baseline_plugin", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _patch_paths(work, input_path):
    os.makedirs(work, exist_ok=True)
    pc_file._INPUT = input_path
    pc_file._OUTPUT = os.path.join(work, "output.bin")


def _run_one(module_path, context, work, input_path, function="main"):
    _patch_paths(work, input_path)
    tmp_dir = tempfile.mkdtemp(prefix="pcd-gen-")
    socket_path = os.path.join(tmp_dir, "runtime.sock")
    stop_event = threading.Event()
    ready_event = threading.Event()
    relay = threading.Thread(
        target=_socket_relay, args=(socket_path, stop_event, ready_event), daemon=True
    )
    relay.start()
    if not ready_event.wait(2.0):
        raise RuntimeError("UDS baseline relay did not start")
    old_socket_path = capabilities._SOCKET_PATH
    capabilities._SOCKET_PATH = socket_path
    capabilities.configure_runtime_transport("baseline-instance-abcdefghijklmnopqrstuvwxyz", "b" * 64)
    try:
        module = _load_module(module_path)
        pycloud.configure(context)
        func = getattr(module, function, None)
        if not callable(func):
            raise RuntimeError(f"入口函数 {function} 在 {module_path} 中不存在")
        result = func(pycloud.current_context())
        return result
    finally:
        capabilities._drop_connection()
        capabilities._SOCKET_PATH = old_socket_path
        stop_event.set()
        relay.join(timeout=1.0)
        shutil.rmtree(tmp_dir, ignore_errors=True)


def _execute_context(plugin, write_perm=True, extra=None):
    context = {
        "execution_id": "baseline-exec",
        "gate_id": "gate-1",
        "plugin_id": plugin,
        "version_id": "1.0.0",
        "content_frozen": False,
        "permissions": ["file.content.read_staging", "file.content.write_pre_activation"],
    }
    if extra:
        context.update(extra)
    return context


def _capability_context(plugin, extra=None):
    context = {
        "execution_id": "baseline-exec-cap",
        "step_id": "step_1",
        "user_id": "user-1",
        "space_id": "space-1",
        "content_frozen": True,
        "invocation": "workflow_capability",
        "input": {"text": "privacy cloud disk\nhello world\nprivacy first\n"},
        "permissions": ["file.content.read"],
    }
    if extra:
        context.update(extra)
    return context


def _input_file(plugin):
    for name in ("input.txt", "input.json", "input.csv"):
        candidate = os.path.join(RWE_ROOT, plugin, name)
        if os.path.exists(candidate):
            return candidate
    return None


def _single_entry(plugin, manifest):
    """返回单一执行入口 (module, function)。

    优先取 manifest.entrypoints.events 中 priority 最小的事件；能力型插件若事件
    缺失则回退到 exports 第一个。module 为包内相对路径（如 src/main.py）。
    """
    entries = _event_entries(manifest)
    if entries:
        return entries[0][0], entries[0][1]
    exports = (manifest or {}).get("exports") or []
    if exports:
        return str(exports[0].get("module") or "src/main.py"), \
               str(exports[0].get("function") or "main")
    raise RuntimeError(f"{plugin} manifest 未声明任何执行入口")


def generate(plugin):
    mode = MODE.get(plugin)
    if mode is None:
        print(f"skip  {plugin}: 无基线模式（异常样本）")
        return None
    manifest = _load_manifest(plugin)
    work = tempfile.mkdtemp(prefix="pcd-gen-work-")
    try:
        if mode == "output-bin":
            context = _execute_context(plugin)
            if plugin == "excel_generate":
                context["permissions"].append("platform.capability.invoke")
            module, function = _single_entry(plugin, manifest)
            _run_one(os.path.join(RWE_ROOT, plugin, module), context, work,
                     _input_file(plugin), function)
            with open(pc_file._OUTPUT, "rb") as stream:
                golden = stream.read()
            mode_label = "output-bin"
        elif mode == "return-json":
            context = _capability_context(plugin)
            if plugin in ("capability_user_info", "path_escape"):
                context["permissions"].append("platform.capability.invoke")
            module, function = _single_entry(plugin, manifest)
            returned = _run_one(os.path.join(RWE_ROOT, plugin, module), context, work,
                                _input_file(plugin) or "/dev/null", function)
            golden = json.dumps(returned, ensure_ascii=False, sort_keys=True).encode("utf-8")
            mode_label = "return-json"
        else:  # chain：按 manifest.entrypoints.events 的 priority 顺序逐入口执行（沙箱语义）
            running = _input_file(plugin)
            last_output = None
            entries = _event_entries(manifest)
            if len(entries) < 2:
                raise RuntimeError(f"{plugin} 的 chain 模式需要至少两个声明入口")
            for index, (module, function, _priority) in enumerate(entries):
                step_work = os.path.join(tempfile.mkdtemp(prefix=f"pcd-chain-{index}-"), "work")
                context = _execute_context(plugin)
                _run_one(os.path.join(RWE_ROOT, plugin, module), context,
                         step_work, running, function)
                running = pc_file._OUTPUT
                last_output = running
            with open(last_output, "rb") as stream:
                golden = stream.read()
            mode_label = "chain"
        target = os.path.join(EXPECTED_DIR, plugin + ".golden")
        with open(target, "wb") as stream:
            stream.write(golden)
        with open(target + ".mode", "w", encoding="utf-8") as stream:
            stream.write(mode_label + "\n")
        print(f"wrote {os.path.relpath(target, ROOT)} ({mode_label}, {len(golden)} bytes)")
        return golden
    finally:
        shutil.rmtree(work, ignore_errors=True)


def main():
    os.makedirs(EXPECTED_DIR, exist_ok=True)
    targets = sys.argv[1:] or sorted(MODE)
    for plugin in targets:
        generate(plugin)


if __name__ == "__main__":
    main()
