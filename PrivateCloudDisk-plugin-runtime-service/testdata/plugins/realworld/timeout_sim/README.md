# timeout_sim 超时模拟

- 行为：`while True` 无限自旋
- 验证：容器在 `ExecutionTimeout`（集成测试 4s）后被强制终止，无残留容器
- 用例：需求二 2.9 / 五 5.10 / 八 8.18
