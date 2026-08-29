"""CSV 数据分析与 Markdown 表格报告（需求二 2.5）。

手动解析简单 CSV（AST 白名单不含 csv 模块），对数值列计算平均值/最大/
最小值/总和，生成 Markdown 表格。
"""
import statistics

from pycloud import file


def _parse_csv(text):
    rows = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        rows.append([cell.strip() for cell in line.split(",")])
    return rows


def _numeric_column(rows, index):
    values = []
    for row in rows[1:]:
        if index < len(row):
            cell = row[index].strip()
            try:
                values.append(float(cell))
            except ValueError:
                continue
    return values


def _markdown_table(header, stats):
    lines = [
        "| 列 | 平均值 | 最大值 | 最小值 | 总和 |",
        "| --- | --- | --- | --- | --- |",
    ]
    for name, values in stats:
        if not values:
            lines.append(f"| {name} | - | - | - | - |")
            continue
        total = sum(values)
        lines.append(
            f"| {name} | {statistics.mean(values):.2f} | "
            f"{max(values):.2f} | {min(values):.2f} | {total:.2f} |"
        )
    return "\n".join(lines)


def main(context):
    text = file.read().decode("utf-8")
    rows = _parse_csv(text)
    header = rows[0] if rows else []
    stats = []
    for index, column in enumerate(header):
        stats.append((column, _numeric_column(rows, index)))
    report = _markdown_table(header, stats)
    file.write_pre_activation(report.encode("utf-8"))
    return {"columns": len(header), "report_length": len(report)}
