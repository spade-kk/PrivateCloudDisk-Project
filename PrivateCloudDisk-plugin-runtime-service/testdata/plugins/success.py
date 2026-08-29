"""成功入口：返回结构化 JSON（沙箱契约 result.json）。"""


def main(context):
    return {
        "ok": True,
        "input": context.get("input", {}),
        "execution_id": context.get("execution_id", ""),
    }
