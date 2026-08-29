#!/usr/bin/env python3
"""将 realworld 测试插件迁移为 .pcdpkg 受约束结构（manifest.yaml + src/ + schemas/）。

设计文档第 7 章：包内必须含 manifest.yaml 与 src/；禁止旧 plugin.yaml 松散目录。
一次性迁移工具：幂等，可重复运行。

用法：
    python3 scripts/migrate_realworld_pcdpkg.py
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RWE = os.path.join(ROOT, "testdata", "plugins", "realworld")

# 每个插件的新结构元数据（id 映射为确定性 UUID 形态，其余沿用既有声明）。
# modules: 需要放入 src/ 的 .py 相对（旧插件目录）路径与函数。
SPECS = {
    "text_stats": {
        "name": "文本统计与 Markdown 摘要生成",
        "permissions": ["file.content.read_staging", "file.content.write_pre_activation"],
        "events": [("src/main.py", "main", 10)],
        "schema": True,
    },
    "json_cleaner": {
        "name": "JSON 数据清洗与规范化",
        "permissions": ["file.content.read_staging", "file.content.write_pre_activation"],
        "events": [("src/main.py", "main", 10)],
        "schema": True,
    },
    "csv_report": {
        "name": "CSV 数据分析与 Markdown 表格报告",
        "permissions": ["file.content.read_staging", "file.content.write_pre_activation"],
        "events": [("src/main.py", "main", 10)],
        "schema": True,
    },
    "excel_generate": {
        "name": "Excel 报表生成模拟",
        "permissions": ["file.content.read_staging", "file.content.write_pre_activation", "platform.capability.invoke"],
        "events": [("src/main.py", "main", 10)],
        "schema": True,
    },
    "excel_parse": {
        "name": "Excel 数据解析与 TXT 报告输出",
        "permissions": ["file.content.read_staging", "file.content.write_pre_activation"],
        "events": [("src/main.py", "main", 10)],
        "schema": True,
    },
    "capability_report": {
        "name": "能力函数导出 generate_report",
        "permissions": ["file.content.read"],
        "events": [("src/main.py", "main", 10)],
        "exports": [{"name": "generate_report", "module": "src/main.py", "function": "build_report",
                     "input_schema": "schemas/capability.generate_report.input.json",
                     "output_schema": "schemas/capability.generate_report.output.json"}],
        "capability_schema": "generate_report",
    },
    "capability_user_info": {
        "name": "用户信息能力调用输出",
        "permissions": ["file.content.read", "platform.capability.invoke"],
        "events": [("src/main.py", "main", 10)],
        "schema": True,
    },
    "multi_entry_pkg": {
        "name": "多步骤入口链插件包",
        "permissions": ["file.content.read_staging", "file.content.write_pre_activation"],
        "events": [("src/step_a.py", "main", 10), ("src/step_b.py", "main", 20)],
        "schema": True,
    },
    "content_reverse": {
        "name": "内容反转修改",
        "permissions": ["file.content.read_staging", "file.content.write_pre_activation"],
        "events": [("src/main.py", "main", 10)],
        "schema": True,
    },
    "path_escape": {
        "name": "路径逃逸尝试",
        "permissions": ["file.content.read_staging", "platform.capability.invoke"],
        "events": [("src/main.py", "main", 10)],
        "schema": False,
    },
    "timeout_sim": {
        "name": "超时模拟插件",
        "permissions": ["file.content.read_staging", "file.content.write_pre_activation"],
        "events": [("src/main.py", "main", 10)],
        "limits": {"timeout_seconds": 2, "memory_mb": 128},
        "schema": False,
    },
    "resource_hog": {
        "name": "资源耗尽模拟插件",
        "permissions": ["file.content.read_staging", "file.content.write_pre_activation"],
        "events": [("src/main.py", "main", 10)],
        "limits": {"timeout_seconds": 15, "memory_mb": 64},
        "schema": False,
    },
    "malicious_import": {
        "name": "恶意模块导入尝试样本",
        "permissions": ["file.content.read_staging", "file.content.write_pre_activation"],
        "events": [("src/main.py", "main", 10)],
        "schema": False,
    },
    "invalid_output": {
        "name": "输出无效文件",
        "permissions": ["file.content.read_staging", "file.content.write_pre_activation"],
        "events": [("src/main.py", "main", 10)],
        "schema": False,
    },
}

OLD = {"src/main.py": "main.py", "src/step_a.py": "step_a.py", "src/step_b.py": "step_b.py"}


def plugin_uuid(plugin_id: str) -> str:
    digest = hashlib.md5(plugin_id.encode("utf-8")).hexdigest()
    return "%s-%s-%s-%s-%s" % (digest[0:8], digest[8:12], digest[12:16], digest[16:20], digest[20:32])


def emit_manifest(plugin_id: str, spec: dict) -> str:
    lines = [
        "manifest_version: 1",
        "plugin:",
        f"  id: {plugin_uuid(plugin_id)}",
        f"  name: {spec['name']}",
        "  type: CLOUD_PLUGIN",
        "  version: 1.0.0",
        "runtime:",
        '  language: python',
        '  version: "3.11"',
        "permissions:",
    ]
    for permission in spec["permissions"]:
        lines.append(f"  - {permission}")
    lines.append("entrypoints:")
    lines.append("  events:")
    for module, function, priority in spec["events"]:
        lines.append(f"  - event: pcd.file.content.ready.v1")
        lines.append(f"    module: {module}")
        lines.append(f"    function: {function}")
        lines.append(f"    priority: {priority}")
        lines.append("    permissions:")
        for permission in spec["permissions"]:
            lines.append(f"      - {permission}")
    if spec.get("exports"):
        lines.append("exports:")
        for export in spec["exports"]:
            lines.append(f"  - name: {export['name']}")
            lines.append(f"    module: {export['module']}")
            lines.append(f"    function: {export['function']}")
            lines.append(f"    input_schema: {export['input_schema']}")
            lines.append(f"    output_schema: {export['output_schema']}")
            lines.append("    permissions:")
            lines.append("      - file.content.read")
    if spec.get("limits"):
        lines.append("limits:")
        if "timeout_seconds" in spec["limits"]:
            lines.append(f"  timeout_seconds: {spec['limits']['timeout_seconds']}")
        if "memory_mb" in spec["limits"]:
            lines.append(f"  memory_mb: {spec['limits']['memory_mb']}")
    return "\n".join(lines) + "\n"


def main() -> int:
    failed = []
    for plugin_id, spec in sorted(SPECS.items()):
        directory = os.path.join(RWE, plugin_id)
        if not os.path.isdir(directory):
            failed.append(plugin_id + ": 目录不存在")
            continue
        src_dir = os.path.join(directory, "src")
        schemas_dir = os.path.join(directory, "schemas")
        os.makedirs(src_dir, exist_ok=True)
        os.makedirs(schemas_dir, exist_ok=True)
        # 移动 .py 模块到 src/（幂等：已存在于 src/ 则跳过）。
        for new_rel, old_name in OLD.items():
            old_path = os.path.join(directory, old_name)
            new_path = os.path.join(directory, new_rel)
            if os.path.exists(old_path) and not os.path.exists(new_path):
                shutil.move(old_path, new_path)
        # 其余顶层 .py（若规范未声明但存在）一并移入 src/。
        for name in os.listdir(directory):
            if name.endswith(".py") and not os.path.exists(os.path.join(src_dir, name)):
                shutil.move(os.path.join(directory, name), os.path.join(src_dir, name))
        # 写入 manifest.yaml。
        if not os.path.exists(os.path.join(directory, "manifest.yaml")):
            with open(os.path.join(directory, "manifest.yaml"), "w", encoding="utf-8") as handle:
                handle.write(emit_manifest(plugin_id, spec))
        # 删除旧 plugin.yaml（已废弃）。
        legacy = os.path.join(directory, "plugin.yaml")
        if os.path.exists(legacy):
            os.remove(legacy)
        # schemas：config.schema.json 与能力 schema。
        if spec.get("schema"):
            schema_path = os.path.join(schemas_dir, "config.schema.json")
            if not os.path.exists(schema_path):
                with open(schema_path, "w", encoding="utf-8") as handle:
                    json.dump({"type": "object", "properties": {}, "additionalProperties": True}, handle)
        if spec.get("capability_schema"):
            cap = spec["capability_schema"]
            for suffix, schema in (("input.json", {"type": "object", "properties": {"text": {"type": "string"}}}),
                                   ("output.json", {"type": "object"})):
                path = os.path.join(schemas_dir, f"capability.{cap}.{suffix}")
                if not os.path.exists(path):
                    with open(path, "w", encoding="utf-8") as handle:
                        json.dump(schema, handle)
        print(f"ok     {plugin_id}")
    if failed:
        print("FAILED:", "\n".join(failed))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
