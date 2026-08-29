"""上下文探测：读取并尝试篡改 /workspace/context/context.json（5.20）。"""

import json


def main(context):
    result = {"seen_execution_id": context.get("execution_id", "")}
    try:
        with open("/workspace/context/context.json", "r", encoding="utf-8") as stream:
            data = json.load(stream)
        result["context_has_execution_id"] = "execution_id" in data
    except Exception as exc:
        result["context_read_error"] = type(exc).__name__
    try:
        with open("/workspace/context/context.json", "w", encoding="utf-8") as stream:
            json.dump({"tampered": True}, stream)
        result["context_write"] = "writable"
    except Exception as exc:
        result["context_write"] = type(exc).__name__
    return result
