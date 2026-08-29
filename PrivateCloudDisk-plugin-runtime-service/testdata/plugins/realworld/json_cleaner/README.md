# json_cleaner JSON 清洗规范化

- 读入：`pycloud.file.read()`（JSON 文本）
- 处理：递归过滤空值 / snake_case 化字段名 / `displayName` 排序
- 写回：`pycloud.file.write_pre_activation()`
- 期望输出：`testdata/expected/json_cleaner.golden`
- 用例：需求二 2.4 / 八 8.6-8.9
