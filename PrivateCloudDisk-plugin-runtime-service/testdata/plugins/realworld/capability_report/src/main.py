"""多步骤能力函数导出（需求二 2.8）。

通过 @capability 装饰器导出命名能力，供工作流能力中心按名发现；
ExecuteCapability 入口 main 接收执行上下文中的 input 并返回结构化 JSON。
"""
from pycloud import capability

_capability_name = "generate_report"


@capability(_capability_name)
def build_report(input_data):
    text = input_data.get("text", "")
    lines = text.splitlines()
    findings = []
    for line in lines:
        if "privacy" in line or "cloud" in line:
            findings.append({"line": len(lines), "matched": line})
    return {
        "status": "ok",
        "line_count": len(lines),
        "keyword_hits": len(findings),
        "markdown": f"# 报告\n\n共 {len(lines)} 行",
    }


def main(context):
    input_data = context.get("input") or {}
    report = build_report(input_data)
    report["step_id"] = context.get("step_id") or ""
    report["user_id"] = context.get("user_id") or ""
    return report
