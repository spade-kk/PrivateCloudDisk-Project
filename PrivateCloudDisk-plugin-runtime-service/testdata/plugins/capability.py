"""工作流能力入口：返回结构化输出（6.13/6.16）。"""


def main(context):
    return {
        "status": "ok",
        "step_id": context.get("step_id", ""),
        "input": context.get("input", {}),
    }
