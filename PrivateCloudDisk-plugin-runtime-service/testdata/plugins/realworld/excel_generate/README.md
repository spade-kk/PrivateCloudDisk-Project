# excel_generate Excel 报表生成模拟

- 读入：`pycloud.file.read()`（销售数据 JSON）
- 能力：`pycloud.call_api("api.file.generate_excel", ...)`，测试环境 mock 返回 CSV 内容
- 写回：`pycloud.file.write_pre_activation()`（CSV 报表）
- 期望输出：`testdata/expected/excel_generate.golden`
- mock 策略：能力网关在测试侧（Docker 集成测试）应答 CSV；见 docs 测试指南
- 用例：需求二 2.6 / 六 6.9 / 八 8.13-8.14
