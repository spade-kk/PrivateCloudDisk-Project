# text_stats 文本统计与 Markdown 摘要生成

- 读入：`pycloud.file.read()`（暂存内容）
- 处理：字符数 / 行数 / 空白行数 / 词频 Top 10
- 写回：`pycloud.file.write_pre_activation()`（Markdown 报告）
- 期望输出：`testdata/expected/text_stats.golden`
- 用例：需求二 2.3 / 八 8.1-8.5
