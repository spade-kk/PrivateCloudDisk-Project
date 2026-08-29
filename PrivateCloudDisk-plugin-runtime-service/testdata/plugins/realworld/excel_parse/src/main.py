"""Excel 数据解析与 TXT 报告输出（需求二 2.7）。

解析 CSV/模拟 Excel 数据，生成包含列与数据行汇总的 TXT 报告。
"""
from pycloud import file


def _parse_csv(text):
    rows = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        rows.append([cell.strip() for cell in line.split(",")])
    return rows


def main(context):
    rows = _parse_csv(file.read().decode("utf-8"))
    header = rows[0] if rows else []
    records = rows[1:] if len(rows) > 1 else []
    report = "\n".join(
        [
            "# 数据解析报告",
            f"- 列：{', '.join(header)}",
            f"- 数据行数：{len(records)}",
        ]
    )
    file.write_pre_activation(report.encode("utf-8"))
    return {"rows": len(records), "columns": len(header)}
