"""Excel 报表生成模拟（需求二 2.6）。

读取销售数据 JSON，调用能力网关 api.file.generate_excel（测试环境由 mock
返回 CSV 内容代替 Excel 二进制），将报表内容写入候选输出。
"""
import json

from pycloud import call_api, file


def main(context):
    raw = file.read().decode("utf-8")
    payload = json.loads(raw)
    response = call_api(
        "api.file.generate_excel",
        {"format": "csv", "rows": payload.get("rows", [])},
    )
    content = response.get("content") or ""
    file.write_pre_activation(content.encode("utf-8"))
    return {
        "content_type": response.get("content_type", ""),
        "length": len(content),
    }
