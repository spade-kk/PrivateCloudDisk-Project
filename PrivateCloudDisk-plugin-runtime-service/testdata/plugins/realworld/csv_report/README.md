# csv_report CSV 数据分析

- 读入：`pycloud.file.read()`（CSV 文本，首行表头）
- 处理：数值列 平均值 / 最大值 / 最小值 / 总和（statistics）
- 写回：`pycloud.file.write_pre_activation()`（Markdown 表格）
- 期望输出：`testdata/expected/csv_report.golden`
- 用例：需求二 2.5 / 八 8.10-8.12
