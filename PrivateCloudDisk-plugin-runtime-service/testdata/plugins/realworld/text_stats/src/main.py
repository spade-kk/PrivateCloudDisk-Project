"""文本统计与 Markdown 摘要生成（需求二 2.3）。

读取输入文件，统计字符/行/空白行与词频 Top 10，输出 Markdown 摘要报告。
所有文件读写均通过 pycloud SDK，不触碰宿主资源。
"""
from collections import Counter

from pycloud import file


def _word_frequency(text, top):
    words = text.split()
    return Counter(words).most_common(top)


def _blank_line_count(lines):
    return sum(1 for line in lines if not line.strip())


def _markdown_report(chars, lines, blanks, top_words):
    header = "# 文本统计报告"
    body = [
        f"- 字符数：{chars}",
        f"- 行数：{lines}",
        f"- 空白行数：{blanks}",
        f"- 非空白行数：{lines - blanks}",
        "",
        "## 词频 Top 10",
        "",
        "| 排名 | 单词 | 次数 |",
        "| --- | --- | --- |",
    ]
    for index, (word, count) in enumerate(top_words, start=1):
        body.append(f"| {index} | {word} | {count} |")
    return header + "\n" + "\n".join(body)


def main(context):
    raw = file.read()
    text = raw.decode("utf-8", errors="replace")
    lines = text.splitlines()
    blanks = _blank_line_count(lines)
    top_words = _word_frequency(text, 10)
    report = _markdown_report(len(text), len(lines), blanks, top_words)
    file.write_pre_activation(report.encode("utf-8"))
    return {"chars": len(text), "lines": len(lines), "blanks": blanks}
