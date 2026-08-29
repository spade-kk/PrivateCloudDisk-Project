# invalid_output 输出无效文件

- 行为：`return {1,2,3}`（set 不可 JSON 序列化）
- 验证：runner 进入安全异常边界，返回 PLUGIN_EXECUTION_FAILED 且错误脱敏
- 用例：需求二 2.15 / 八 8.25
