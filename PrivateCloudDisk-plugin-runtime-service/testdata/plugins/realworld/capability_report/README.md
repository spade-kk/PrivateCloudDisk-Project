# capability_report 能力函数导出

- 导出：`@capability("generate_report")` 装饰器标记
- 执行：ExecuteCapability 调用入口 `main`，读取 `context.input`
- 返回：结构化 JSON（line_count / keyword_hits / markdown / step_id / user_id）
- 用例：需求二 2.8 / 八 8.16-8.17
